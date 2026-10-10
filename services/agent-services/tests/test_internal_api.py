"""契约测试：响应形状对照 Java HttpAgentClient（设计 V1 §5 对照表）。

关键断言：错误体为顶层扁平 JSON（errorCode / providerStatus），
Java mapErrorBody 不解析 {"detail": {...}} 嵌套。
"""

from fastapi.testclient import TestClient
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.app import create_app
from conftest import TOKEN_HEADER, make_settings


def _blocked_client() -> TestClient:
    return TestClient(create_app(make_settings()))


def _live_client(reply_text: str = "hello from test model") -> TestClient:
    handle = ModelHandle(
        model=TestModel(custom_output_text=reply_text),
        spec="test:model",
        blocked_reason=None,
    )
    return TestClient(create_app(make_settings(), model_handle=handle))


def test_chat_blocked_returns_503_flat_body() -> None:
    response = _blocked_client().post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "10", "message": "hello"},
    )
    assert response.status_code == 503
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"
    assert body["providerStatus"] == "BLOCKED"
    assert "missing AI_MODEL" in body["message"]
    assert "detail" not in body
    assert "data" not in body


def test_health_reports_blocked_without_model() -> None:
    response = _blocked_client().get("/internal/v1/health", headers=TOKEN_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["service"] == "agent-service"
    assert body["provider"] == "blocked"
    assert body["providerStatus"] == "BLOCKED"
    assert body["retrievalStatus"] == "BLOCKED"
    assert "RETRIEVAL_DATABASE_URL" in body["retrievalBlockedReason"]


def test_health_reports_live_with_model() -> None:
    response = _live_client().get("/internal/v1/health", headers=TOKEN_HEADER)
    assert response.status_code == 200
    body = response.json()
    assert body["providerStatus"] == "LIVE"
    assert body["provider"] == "test:model"


def test_chat_success_shape_matches_java_contract() -> None:
    response = _live_client("こんにちは").post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={
            "siteId": "10",
            "message": "営業時間は？",
            "conversationId": "conv-1",
            "promptProfile": {"systemPrompt": "You are a garage assistant.", "temperature": 0.3},
        },
    )
    assert response.status_code == 200
    body = response.json()
    # Java 侧要求：ok=true、data.reply 非空、data.providerStatus == "LIVE"
    assert body["ok"] is True
    assert body["data"]["reply"] == "こんにちは"
    assert body["data"]["conversationId"] == "conv-1"
    assert body["data"]["providerStatus"] == "LIVE"


def test_chat_success_returns_todos_snapshot_from_request() -> None:
    # 关闭 todo 工具，避免 TestModel 自动调用工具改写快照；只验证 Java→Python→Java 往返形状
    handle = ModelHandle(model=TestModel(custom_output_text="ok"), spec="test:model", blocked_reason=None)
    client = TestClient(create_app(make_settings(agent_enable_todo_tools=False), model_handle=handle))
    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={
            "siteId": "10",
            "message": "continue",
            "todos": [{"id": "t1", "title": "Step 1", "status": "IN_PROGRESS"}],
        },
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["todos"] == [{"id": "t1", "title": "Step 1", "status": "IN_PROGRESS"}]
    assert isinstance(data["skillsApplied"], list)


def test_chat_accepts_history_longer_than_message_cap() -> None:
    from agent_services.schemas.chat import MAX_MESSAGE_CHARS

    response = _live_client("ok").post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={
            "siteId": "10",
            "message": "next",
            "history": [
                {"role": "USER", "content": "earlier"},
                {"role": "ASSISTANT", "content": "x" * (MAX_MESSAGE_CHARS + 80)},
            ],
        },
    )
    assert response.status_code == 200
    assert response.json()["data"]["reply"] == "ok"


def test_chat_oversized_request_returns_400_flat_body() -> None:
    from agent_services.schemas.todo import MAX_TODO_ITEMS

    too_many = [{"id": f"t{i}", "title": "x"} for i in range(MAX_TODO_ITEMS + 1)]
    response = _live_client().post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "10", "message": "hello", "todos": too_many},
    )
    assert response.status_code == 400
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "BAD_REQUEST"
    assert "todos" in body["message"]
    assert "detail" not in body


def test_chat_blank_input_returns_400_flat_body() -> None:
    response = _live_client().post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "  ", "message": "hello"},
    )
    assert response.status_code == 400
    body = response.json()
    assert body["errorCode"] == "BAD_REQUEST"
    assert "detail" not in body


def test_invalid_ai_model_spec_starts_blocked_not_crash() -> None:
    # spec 非法/凭证缺失 → 服务正常启动 + BLOCKED，而非启动失败（与 token 缺失语义相反）
    client = TestClient(create_app(make_settings(ai_model="openai:gpt-4o-mini")))
    response = client.get("/internal/v1/health", headers=TOKEN_HEADER)
    assert response.status_code == 200
    assert response.json()["providerStatus"] == "BLOCKED"
