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
