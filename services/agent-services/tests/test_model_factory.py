"""§22.6 T3：主模型 + Fallback 链；主模型 BLOCKED 时 fallback 不得顶替（ADR-0004）。"""

import pydantic_ai.models
import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.exceptions import UserError
from pydantic_ai.models import Model
from pydantic_ai.models.fallback import FallbackModel
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle, create_model
from agent_services.app import create_app


@pytest.fixture(autouse=True)
def fake_infer_model(monkeypatch: pytest.MonkeyPatch) -> None:
    """无需真实凭证：fake:* → TestModel，其余视为无效 spec。"""

    def infer(spec: str) -> Model:
        if spec.startswith("fake:"):
            return TestModel(model_name=spec)
        raise UserError(f"Unknown model: {spec}")

    monkeypatch.setattr(pydantic_ai.models, "infer_model", infer)


def test_no_fallback_configured_returns_plain_model() -> None:
    handle = create_model(make_settings(ai_model="fake:primary"))
    assert handle.live
    assert isinstance(handle.model, TestModel)
    assert handle.fallback_specs == ()


def test_fallbacks_wrap_primary_and_skip_duplicates_and_invalid() -> None:
    handle = create_model(
        make_settings(
            ai_model="fake:primary",
            ai_fallback_models="fake:primary, bad:x ,,fake:backup",
        )
    )
    assert isinstance(handle.model, FallbackModel)
    # 与主模型相同的 spec 被跳过，无效 spec 被剔除
    assert handle.fallback_specs == ("fake:backup",)


def test_blocked_primary_is_not_replaced_by_fallback() -> None:
    handle = create_model(make_settings(ai_model="", ai_fallback_models="fake:backup"))
    assert not handle.live
    assert handle.fallback_specs == ()

    handle = create_model(make_settings(ai_model="bad:x", ai_fallback_models="fake:backup"))
    assert not handle.live
    assert handle.fallback_specs == ()
    assert handle.blocked_reason == "AI provider is not configured or invalid: UserError"
    assert "bad:x" not in (handle.blocked_reason or "")


def test_health_redacts_url_like_model_spec() -> None:
    handle = ModelHandle(
        model=TestModel(),
        spec="https://user:secret@llm.internal/v1",
        blocked_reason=None,
        fallback_specs=("https://backup.internal/v1",),
    )
    app = create_app(make_settings(), model_handle=handle)
    body = TestClient(app).get("/internal/v1/health", headers=TOKEN_HEADER).json()
    assert body["provider"] == "redacted"
    assert body["fallbackProviders"] == ["redacted"]
    assert "secret" not in str(body)
    assert "llm.internal" not in str(body)


def test_health_reports_fallback_providers() -> None:
    app = create_app(make_settings(ai_model="fake:primary", ai_fallback_models="fake:backup"))
    body = TestClient(app).get("/internal/v1/health", headers=TOKEN_HEADER).json()
    assert body["providerStatus"] == "LIVE"
    assert body["fallbackProviders"] == ["fake:backup"]


def test_health_reports_effective_limits_with_null_for_unlimited() -> None:
    app = create_app(
        make_settings(
            agent_max_requests=7,
            agent_max_tool_calls=0,
            agent_tool_timeout_seconds=12.5,
            agent_history_max_chars=1000,
        )
    )
    limits = TestClient(app).get("/internal/v1/health", headers=TOKEN_HEADER).json()["limits"]
    assert limits == {
        "maxRequests": 7,
        "maxToolCalls": None,
        "maxTotalTokens": None,
        "toolTimeoutSeconds": 12.5,
        "historyMaxChars": 1000,
    }


def _capture_google_build(monkeypatch: pytest.MonkeyPatch) -> dict[str, object]:
    captured: dict[str, object] = {}

    def fake_build(model_name: str, *, api_key: str | None, base_url: str | None) -> Model:
        captured["model_name"] = model_name
        captured["api_key"] = api_key
        captured["base_url"] = base_url
        return TestModel(model_name=model_name)

    monkeypatch.setattr("agent_services.agent.model_factory._build_google_model", fake_build)
    return captured


def test_google_model_passes_name_and_direct_credentials(monkeypatch: pytest.MonkeyPatch) -> None:
    captured = _capture_google_build(monkeypatch)
    handle = create_model(
        make_settings(
            ai_model="google:gemini-3-flash-preview",
            ai_provider_api_key="test-provider-key",
            ai_provider_base_url="https://generativelanguage.example/v1beta",
        )
    )
    assert captured == {
        "model_name": "gemini-3-flash-preview",
        "api_key": "test-provider-key",
        "base_url": "https://generativelanguage.example/v1beta",
    }
    assert handle.live
    assert isinstance(handle.model, TestModel)
    assert handle.spec == "google:gemini-3-flash-preview"


def test_google_gla_model_normalizes_name_and_passes_direct_credentials(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured = _capture_google_build(monkeypatch)
    handle = create_model(
        make_settings(
            ai_model="google-gla:gemini-3-flash-preview",
            ai_provider_api_key="test-provider-key",
            ai_provider_base_url="https://generativelanguage.example/v1beta",
        )
    )
    assert captured == {
        "model_name": "gemini-3-flash-preview",
        "api_key": "test-provider-key",
        "base_url": "https://generativelanguage.example/v1beta",
    }
    assert handle.live
    assert isinstance(handle.model, TestModel)
    assert handle.spec == "google-gla:gemini-3-flash-preview"
