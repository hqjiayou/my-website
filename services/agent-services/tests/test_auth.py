import json

import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient

from agent_services.app import create_app
from agent_services.config import Settings


def test_missing_token_config_fails_startup() -> None:
    # 安全配置错误必须 fail-fast，不允许服务以 503 模式裸奔
    with pytest.raises(RuntimeError, match="AGENT_INTERNAL_SERVICE_TOKEN"):
        create_app(make_settings(agent_internal_service_token=""))


def test_whitespace_token_config_fails_startup() -> None:
    with pytest.raises(RuntimeError):
        create_app(make_settings(agent_internal_service_token="   "))


def test_invalid_skills_unknown_policy_fails_startup() -> None:
    with pytest.raises(RuntimeError, match="drop") as exc_info:
        create_app(make_settings(agent_skills_unknown_policy="drop"))
    message = str(exc_info.value)
    assert "ignore" in message
    assert "reject" in message


def test_mixed_case_skills_unknown_policy_is_stored_canonical() -> None:
    settings = Settings(agent_skills_unknown_policy=" Reject ")
    assert settings.agent_skills_unknown_policy == "reject"
    create_app(make_settings(agent_skills_unknown_policy=" Reject "))


def test_contract_test_model_refused_in_production() -> None:
    with pytest.raises(RuntimeError, match="AGENT_CONTRACT_USE_TEST_MODEL") as exc_info:
        create_app(
            make_settings(
                agent_contract_use_test_model=True,
                deployment_environment="production",
            )
        )
    message = str(exc_info.value)
    assert "local/test" in message
    assert "production" in message


def test_contract_test_model_allowed_in_local() -> None:
    create_app(
        make_settings(
            agent_contract_use_test_model=True,
            deployment_environment="local",
        )
    )


def test_contract_test_model_off_allows_production() -> None:
    create_app(
        make_settings(
            agent_contract_use_test_model=False,
            deployment_environment="production",
        )
    )


def test_request_without_token_is_401_flat_body() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.post("/internal/v1/chat", json={"siteId": "1", "message": "hello"})
    assert response.status_code == 401
    body = response.json()
    # 契约：顶层扁平字段，无 detail 嵌套
    assert body["ok"] is False
    assert body["errorCode"] == "UNAUTHORIZED"
    assert "detail" not in body


def test_request_with_wrong_token_is_401() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.get(
        "/internal/v1/health",
        headers={"X-Internal-Service-Token": "wrong-token"},
    )
    assert response.status_code == 401


async def test_non_ascii_token_is_401_flat_body_not_500() -> None:
    # httpx 拒绝非 ASCII 的 str header。直接走 ASGI，用 latin-1 字节送入 "tökén"。
    app = create_app(make_settings())
    sent: list[dict[str, object]] = []

    async def receive() -> dict[str, object]:
        return {"type": "http.request", "body": b"", "more_body": False}

    async def send(message: dict[str, object]) -> None:
        sent.append(message)

    async with app.router.lifespan_context(app):
        await app(
            {
                "type": "http",
                "asgi": {"version": "3.0"},
                "http_version": "1.1",
                "method": "GET",
                "scheme": "http",
                "path": "/internal/v1/health",
                "raw_path": b"/internal/v1/health",
                "query_string": b"",
                "headers": [(b"x-internal-service-token", "tökén".encode("latin-1"))],
                "client": ("127.0.0.1", 123),
                "server": ("test", 80),
            },
            receive,
            send,
        )

    assert sent[0]["status"] == 401
    raw = b"".join(message.get("body", b"") for message in sent if message["type"] == "http.response.body")
    body = json.loads(raw)
    assert body["ok"] is False
    assert body["errorCode"] == "UNAUTHORIZED"
    assert "message" in body
    assert "detail" not in body
    assert "TypeError" not in raw.decode()


def test_request_with_valid_token_passes() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.get("/internal/v1/health", headers=TOKEN_HEADER)
    assert response.status_code == 200


def test_public_health_needs_no_token() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
