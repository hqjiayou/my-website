"""SSE /internal/v1/chat/stream 契约测试。"""

from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.app import create_app


def test_chat_stream_emits_application_events() -> None:
    handle = ModelHandle(model=TestModel(custom_output_text="streamed"), spec="test:stream", blocked_reason=None)
    client = TestClient(create_app(make_settings(), model_handle=handle))

    with client.stream(
        "POST",
        "/internal/v1/chat/stream",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    ) as response:
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("text/event-stream")
        body = response.read().decode("utf-8")

    assert "event: START" in body
    assert "event: TEXT_DELTA" in body
    assert "event: DONE" in body
    assert "streamed" in body
    assert "providerStatus" in body


def test_chat_stream_blocked_without_model() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.post(
        "/internal/v1/chat/stream",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    )
    assert response.status_code == 503
    body = response.json()
    assert body["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"
    assert "detail" not in body
