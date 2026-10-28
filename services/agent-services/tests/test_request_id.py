"""§22.7 T4：X-Request-Id 绑定到运行上下文 → Agent run metadata。"""

from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.runtime import RunBudget, build_run_kwargs
from agent_services.app import create_app
from agent_services.observability.request_id import current_request_id


def _capturing_app(seen: list[str | None]):
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(current_request_id())
        return ModelResponse(parts=[TextPart("ok")])

    handle = ModelHandle(model=FunctionModel(respond), spec="test:fn", blocked_reason=None)
    return create_app(make_settings(agent_enable_todo_tools=False), model_handle=handle)


def test_valid_request_id_is_visible_during_run() -> None:
    seen: list[str | None] = []
    response = TestClient(_capturing_app(seen)).post(
        "/internal/v1/chat",
        headers={**TOKEN_HEADER, "X-Request-Id": "req-abc_123"},
        json={"agentId": "1", "message": "hi"},
    )
    assert response.status_code == 200
    assert seen == ["req-abc_123"]
    # 请求结束后上下文复位，不串到后续请求
    assert current_request_id() is None


def test_invalid_request_id_is_dropped() -> None:
    seen: list[str | None] = []
    TestClient(_capturing_app(seen)).post(
        "/internal/v1/chat",
        headers={**TOKEN_HEADER, "X-Request-Id": "bad id\twith spaces"},
        json={"agentId": "1", "message": "hi"},
    )
    assert seen == [None]


def test_run_kwargs_carry_request_id_metadata_only_when_bound() -> None:
    assert "metadata" not in build_run_kwargs(None, None, RunBudget())
