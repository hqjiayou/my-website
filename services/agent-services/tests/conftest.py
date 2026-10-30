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
        "gemini_api_key": "",
        "ai_fallback_models": "",
        # 显式空掉，避免开发机 .env 里的检索配置把「未配置」用例变成已配置。
        "retrieval_database_url": "",
        "ai_embedding_model": "",
        "ai_embedding_dimensions": 1536,
        # 显式关闭，避免 shell/CI 残留 AGENT_CONTRACT_USE_TEST_MODEL 污染 BLOCKED 契约测试
        "agent_contract_use_test_model": False,
        # 测试夹具代表本机调试形态：无入口时允许进程模型（注入 handle）作答；
        # 闸门关闭形态由 test_internal_api 的专用用例以 False 覆盖验证（Issue #145 F1）
        "ai_allow_process_model": True,
    }
    base.update(overrides)
    return Settings(**base)  # type: ignore[arg-type]


TOKEN_HEADER = {"X-Internal-Service-Token": "test-internal-token"}
