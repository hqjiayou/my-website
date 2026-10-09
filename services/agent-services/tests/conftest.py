"""测试全局护栏：禁止任何测试触达真实 Provider（设计 V1 §10 第 2 层）。"""

import pydantic_ai.models

pydantic_ai.models.ALLOW_MODEL_REQUESTS = False

from agent_services.config import Settings  # noqa: E402


def make_settings(**overrides: object) -> Settings:
    base: dict[str, object] = {
        "port": 3001,
        "agent_internal_service_token": "test-internal-token",
        "ai_model": "",
        "ai_provider_api_key": "",
        "ai_provider_base_url": "",
        "ai_fallback_models": "",
        # 显式关闭，避免 shell/CI 残留 AGENT_CONTRACT_USE_TEST_MODEL 污染 BLOCKED 契约测试
        "agent_contract_use_test_model": False,
    }
    base.update(overrides)
    return Settings(**base)  # type: ignore[arg-type]


TOKEN_HEADER = {"X-Internal-Service-Token": "test-internal-token"}
