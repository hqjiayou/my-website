import pytest
from fastapi.testclient import TestClient

from agent_services.app import create_app
from conftest import TOKEN_HEADER, make_settings


def test_missing_token_config_fails_startup() -> None:
    # 安全配置错误必须 fail-fast，不允许服务以 503 模式裸奔
    with pytest.raises(RuntimeError, match="AGENT_INTERNAL_SERVICE_TOKEN"):
        create_app(make_settings(agent_internal_service_token=""))


def test_whitespace_token_config_fails_startup() -> None:
    with pytest.raises(RuntimeError):
        create_app(make_settings(agent_internal_service_token="   "))


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


def test_request_with_valid_token_passes() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.get("/internal/v1/health", headers=TOKEN_HEADER)
    assert response.status_code == 200


def test_public_health_needs_no_token() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
