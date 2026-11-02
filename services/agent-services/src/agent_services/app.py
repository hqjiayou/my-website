import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from agent_services import __version__
from agent_services.agent.model_factory import ModelHandle, create_model
from agent_services.api.errors import register_error_handlers
from agent_services.api.internal import router as internal_router
from agent_services.auth.internal_token import assert_token_configured
from agent_services.config import (
    Settings,
    assert_contract_test_model_allowed,
    assert_retrieval_max_chunks_per_source,
    assert_retrieval_min_score,
    assert_retrieval_score_ratio_floor,
    assert_skills_max_instruction_chars,
    assert_skills_unknown_policy,
    get_settings,
    missing_retrieval_settings,
    retrieval_not_configured_message,
)
from agent_services.observability.otel import setup_otel
from agent_services.observability.request_id import install_request_id_middleware
from agent_services.retrieval.runtime_config import (
    sync_runtime_config_from_business_api,
)
from agent_services.retrieval.service import RetrievalService, create_retrieval_service

logger = logging.getLogger(__name__)


@asynccontextmanager
async def _lifespan(app: FastAPI):
    # 启动反向收敛（#146）：向 Java 拉页面配置盖过 env 基线；拉取失败只记
    # warning 按 env 继续，绝不阻断启动（Python 先于 Java 起是常态）。
    await sync_runtime_config_from_business_api(app)
    yield
    if app.state.retrieval_service is not None:
        await app.state.retrieval_service.close()


def create_app(
    settings: Settings | None = None,
    model_handle: ModelHandle | None = None,
    retrieval_service: RetrievalService | None = None,
) -> FastAPI:
    """FastAPI factory。

    model_handle / retrieval_service 参数仅供测试注入；
    生产路径一律由 AI_MODEL / RETRIEVAL_DATABASE_URL 等配置创建。
    """
    resolved = settings or get_settings()
    # 安全配置错误必须 fail-fast，与 Provider BLOCKED（可正常启动）严格区分
    assert_token_configured(resolved.agent_internal_service_token)
    assert_skills_unknown_policy(resolved.agent_skills_unknown_policy)
    assert_skills_max_instruction_chars(resolved.agent_skills_max_instruction_chars)
    assert_retrieval_min_score(resolved.retrieval_min_score)
    assert_retrieval_score_ratio_floor(resolved.retrieval_score_ratio_floor)
    assert_retrieval_max_chunks_per_source(resolved.retrieval_max_chunks_per_source)
    assert_contract_test_model_allowed(resolved)

    app = FastAPI(title="agent-services", version=__version__, lifespan=_lifespan)
    app.state.settings = resolved
    # 已生效的页面配置版本（#146）；env 基线为 0，推送/启动拉取换入后递增。
    app.state.runtime_config_version = 0
    app.state.model_handle = model_handle if model_handle is not None else create_model(resolved)

    if retrieval_service is not None:
        app.state.retrieval_service = retrieval_service
        app.state.retrieval_blocked_reason = None
        # 测试注入的检索服务不套用进程缺项，避免夹具被空配置拦住。
        app.state.retrieval_missing_settings = []
    else:
        missing = missing_retrieval_settings(resolved)
        app.state.retrieval_missing_settings = missing
        if missing:
            logger.warning("Retrieval configuration is missing: %s", ", ".join(missing))
        service, blocked_reason = create_retrieval_service(resolved)
        app.state.retrieval_service = service
        app.state.retrieval_blocked_reason = (
            retrieval_not_configured_message(missing) if missing else blocked_reason
        )

    register_error_handlers(app)
    app.include_router(internal_router)
    # 须先于 setup_otel：FastAPIInstrumentor 后注册 → 位于外层，中间件内可取到 server span
    install_request_id_middleware(app)
    setup_otel(app)

    @app.get("/health")
    async def public_health() -> dict[str, str]:
        # 公开探活只报进程存活，不暴露 Provider 状态
        return {"status": "ok"}

    return app
