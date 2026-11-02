"""运行配置热替换与启动反向收敛（Issue #146 UoW-2）。

页面配置（Java agent_runtime_config）是真相源：Java 保存后推送四项到
PUT /internal/v1/config/retrieval，Python 换入后不重启即生效；Python 启动时
再向 Java 拉一次当前配置收敛（Java 没起/拉取失败则按 env 基线继续，不阻断启动）。

换入语义（定案）：
- configVersion 单调递增防旧：不大于当前已生效版本的推送一律 stale 拒绝；
- 先建后换：候选 service 建好并对 store 显式探活通过，才一次性替换
  app.state 四件套（settings / retrieval_service / retrieval_blocked_reason /
  retrieval_missing_settings）＋ runtime_config_version；失败不换任何 state；
- store 是懒连接（构造只存 DSN、连接池首次使用才建），所以探活必须真实连一次，
  只建得出对象不算成功——坏 DSN 换入会顶掉旧配置，直到首个请求才爆；
- 候选缺项不是失败：页面可故意清空成未配置态，照常换入，health 点名缺项；
- 维度变更不阻止换入，后果走既有 DimensionChangeRequired / reembed 链；
  但换入时若现列维度与新维度不一致，先把 blocked_reason 置成「维度待重嵌」，
  避免 health 在 reembed 完成前谎报 LIVE。

DSN 与 Gemini key 只存在于 Settings 副本与 service 内部：本模块的日志与
返回原因一律不带正文（照 internal token「永不写日志」先例）。
"""

import logging
from dataclasses import dataclass, field

import httpx
from fastapi import FastAPI

from agent_services.config import (
    Settings,
    missing_retrieval_settings,
    retrieval_not_configured_message,
)
from agent_services.retrieval.service import RetrievalService, create_retrieval_service

logger = logging.getLogger(__name__)

RUNTIME_CONFIG_PULL_PATH = "/internal/agent/v1/runtime-config"
STARTUP_PULL_TIMEOUT_SECONDS = 3.0


@dataclass(frozen=True)
class RuntimeConfigValues:
    """页面配置四项的明文值。None ＝该项未配置（推送缺席 / 拉取为 null 同义）。"""

    config_version: int
    retrieval_database_url: str | None = field(default=None, repr=False)
    ai_embedding_model: str | None = None
    ai_embedding_dimensions: int | None = None
    gemini_api_key: str | None = field(default=None, repr=False)


@dataclass(frozen=True)
class ApplyResult:
    applied: bool
    applied_version: int
    blocked_reason: str | None = None


def _candidate_settings(base: Settings, values: RuntimeConfigValues) -> Settings:
    """以当前生效 settings 为基底造候选，只覆盖四项。

    基底不用 env 原值：BUSINESS_API_BASE_URL 等字段的现行生效值都在
    app.state.settings 上，打回 env 会把已收敛的状态倒退回去。
    维度缺席按「未配置」收敛成 0（missing_retrieval_settings 的既有口径）。
    """
    dimensions = values.ai_embedding_dimensions
    if isinstance(dimensions, bool) or not isinstance(dimensions, int):
        dimensions = 0
    return base.model_copy(
        update={
            "retrieval_database_url": values.retrieval_database_url or "",
            "ai_embedding_model": values.ai_embedding_model or "",
            "ai_embedding_dimensions": dimensions,
            "gemini_api_key": values.gemini_api_key or "",
        }
    )


async def _probe_store(service: RetrievalService) -> None:
    """对新 store 做一次真实连接验证。懒连接下这是判定换入成败的核心步。"""
    store = service._store  # noqa: SLF001 — 同包内取 store 探活，无公开入口
    get_pool = getattr(store, "_get_pool", None)
    if callable(get_pool):
        pool = await get_pool()
        async with pool.acquire() as conn:
            await conn.fetchval("SELECT 1")
        return
    # 其它 store 实现（测试替身等）：走协议方法触发真实连接。
    await store.current_dimensions()


async def _column_dimensions(service: RetrievalService) -> int | None:
    """读现列维度失败不挡换入：按未知处理，后果仍有 reembed 链兜底。"""
    try:
        return await service._store.current_dimensions()  # noqa: SLF001
    except Exception:  # noqa: BLE001
        logger.warning("runtime config: could not read current index dimensions", exc_info=True)
        return None


async def _close_quietly(service: RetrievalService | None) -> None:
    if service is None:
        return
    try:
        await service.close()
    except Exception:  # noqa: BLE001 — 关旧资源失败只记日志，不回滚已换入的新状态
        logger.warning("runtime config: closing a retrieval service failed", exc_info=True)


async def apply_runtime_config(
    app: FastAPI,
    values: RuntimeConfigValues,
    *,
    service_factory=None,
) -> ApplyResult:
    """把一份页面配置换入 app.state。失败时不换任何 state，旧配置继续跑。"""
    state = app.state
    current_version = getattr(state, "runtime_config_version", 0)
    if not isinstance(current_version, int) or isinstance(current_version, bool):
        current_version = 0
    if values.config_version <= current_version:
        return ApplyResult(
            applied=False,
            applied_version=current_version,
            blocked_reason=(
                f"Stale runtime config version {values.config_version} "
                f"(current {current_version}); keeping the running configuration."
            ),
        )

    candidate = _candidate_settings(state.settings, values)
    missing = missing_retrieval_settings(candidate)
    if missing:
        # 清空成未配置是合法目标态，不是失败：照常换入，health 点名缺项。
        new_service = None
        new_blocked_reason = retrieval_not_configured_message(missing)
    else:
        factory = service_factory or create_retrieval_service
        new_service, factory_blocked_reason = factory(candidate)
        if new_service is None:
            return ApplyResult(
                applied=False,
                applied_version=current_version,
                blocked_reason=(
                    factory_blocked_reason
                    or "Retrieval service could not be built from the pushed configuration."
                ),
            )
        try:
            await _probe_store(new_service)
        except Exception as exc:  # noqa: BLE001 — 探活失败＝构建失败，整套不换
            await _close_quietly(new_service)
            logger.warning(
                "runtime config v%s rejected: retrieval store probe failed (%s)",
                values.config_version,
                type(exc).__name__,
            )
            return ApplyResult(
                applied=False,
                applied_version=current_version,
                blocked_reason=(
                    "Retrieval database connection check failed; "
                    "the previous configuration remains active."
                ),
            )
        new_blocked_reason = factory_blocked_reason
        if new_blocked_reason is None:
            column_dimensions = await _column_dimensions(new_service)
            if (
                column_dimensions is not None
                and column_dimensions != candidate.ai_embedding_dimensions
            ):
                # 检索在维度对齐前不可用（向量维度不匹配会在存储层报错、
                # 经 retrieve_chunks 收敛成 degraded 空结果），状态先诚实置 BLOCKED；
                # 索引 prepare 路径不受此状态影响，reembed 照常可走。
                new_blocked_reason = (
                    f"Embedding dimensions changed to {candidate.ai_embedding_dimensions}, "
                    f"but the index column is still {column_dimensions}; "
                    "retrieval is blocked until re-embedding completes."
                )

    old_service = state.retrieval_service
    state.settings = candidate
    state.retrieval_service = new_service
    state.retrieval_blocked_reason = new_blocked_reason
    state.retrieval_missing_settings = missing
    state.runtime_config_version = values.config_version
    if old_service is not None and old_service is not new_service:
        await _close_quietly(old_service)
    logger.info(
        "runtime config v%s applied (retrieval %s)",
        values.config_version,
        "configured" if new_service is not None else "not configured",
    )
    return ApplyResult(applied=True, applied_version=values.config_version)


def _optional_str(data: dict, key: str) -> str | None:
    value = data.get(key)
    return value if isinstance(value, str) else None


def _values_from_pull_data(data: dict) -> RuntimeConfigValues | None:
    version = data.get("configVersion")
    if isinstance(version, bool) or not isinstance(version, int) or version < 1:
        return None
    dimensions = data.get("aiEmbeddingDimensions")
    if isinstance(dimensions, bool) or not isinstance(dimensions, int):
        dimensions = None
    return RuntimeConfigValues(
        config_version=version,
        retrieval_database_url=_optional_str(data, "retrievalDatabaseUrl"),
        ai_embedding_model=_optional_str(data, "aiEmbeddingModel"),
        ai_embedding_dimensions=dimensions,
        gemini_api_key=_optional_str(data, "geminiApiKey"),
    )


async def sync_runtime_config_from_business_api(
    app: FastAPI,
    *,
    transport: httpx.AsyncBaseTransport | None = None,
) -> None:
    """启动反向收敛：向 Java 拉当前页面配置并走同一条 apply 路径。

    任何失败（Java 没起、超时、401、坏 JSON、未配置）都只记 warning 并按
    env 基线继续，绝不阻断启动；后续页面保存推送仍可收敛。
    """
    settings = app.state.settings
    base_url = settings.business_api_base_url.strip().rstrip("/")
    if not base_url:
        return
    try:
        async with httpx.AsyncClient(
            base_url=base_url,
            timeout=STARTUP_PULL_TIMEOUT_SECONDS,
            transport=transport,
        ) as client:
            response = await client.get(
                RUNTIME_CONFIG_PULL_PATH,
                headers={"X-Internal-Service-Token": settings.agent_internal_service_token},
            )
        response.raise_for_status()
        body = response.json()
        if not isinstance(body, dict) or body.get("code") != 0:
            logger.warning("runtime config startup pull returned a non-success envelope")
            return
        data = body.get("data")
        if not isinstance(data, dict) or data.get("configured") is not True:
            logger.info("runtime config startup pull: no page configuration saved yet")
            return
        values = _values_from_pull_data(data)
        if values is None:
            logger.warning("runtime config startup pull returned a malformed payload")
            return
        result = await apply_runtime_config(app, values)
        if not result.applied:
            logger.warning(
                "runtime config startup pull was not applied: %s",
                result.blocked_reason or "unknown reason",
            )
    except Exception as exc:  # noqa: BLE001 — 拉取失败无感回退 env 基线
        logger.warning(
            "runtime config startup pull failed (%s); continuing with env configuration",
            type(exc).__name__,
        )
