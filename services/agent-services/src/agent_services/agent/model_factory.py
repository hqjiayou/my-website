"""AI_MODEL spec → Pydantic AI Model。

不自建 Provider 抽象层：Model/Provider 两层抽象直接复用 Pydantic AI（设计 V1 §2）。
无配置 / 凭证缺失 → BLOCKED（ADR-0004），不默认任何模型。
AI_FALLBACK_MODELS（§22.6）仅在主模型可用时生效：主模型 BLOCKED 时 fallback 不得顶替，
否则等同“默认模型”，违反 ADR-0004。Java 模型入口同样如此：入口构建成功后才挂备用链。
"""

import logging
from dataclasses import dataclass, field
from typing import NamedTuple

from pydantic_ai.exceptions import ModelAPIError
from pydantic_ai.models import Model

from agent_services.api.errors import external_failure_message
from agent_services.config import Settings

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ModelHandle:
    model: Model | None
    spec: str
    blocked_reason: str | None
    fallback_specs: tuple[str, ...] = field(default=())

    @property
    def live(self) -> bool:
        return self.model is not None


class EntryFallback(NamedTuple):
    """Java 模型入口链里的一个备援候选：自带 spec / 凭证，不复用主入口的 key。"""

    spec: str
    api_key: str | None
    base_url: str | None


def public_model_spec(spec: str) -> str:
    """健康检查只展示模型名。带 URL 或账号的 spec 不外传。"""
    if "://" in spec or "@" in spec:
        return "redacted"
    return spec


def _parse_specs(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def _google_model_name(spec: str) -> str | None:
    """google: 与旧别名 google-gla:。两者都不是前缀包含关系。"""
    for prefix in ("google-gla:", "google:"):
        if spec.startswith(prefix):
            return spec.removeprefix(prefix)
    return None


def _google_thinking_settings(model_name: str) -> dict[str, object] | None:
    """思考型 Gemini（2.5/3 系）开启 include_thoughts，思考片段才随流返回。

    非思考型模型不带这个配置，避免 API 直接拒绝请求；provider 不返回
    思考片段时流式层自然没有 THINKING 事件，不伪造。
    """
    if model_name.startswith(("gemini-3", "gemini-2.5")):
        return {"google_thinking_config": {"include_thoughts": True}}
    return None


def _build_google_model(model_name: str, *, api_key: str | None, base_url: str | None) -> Model:
    """延迟导入，避免其它 provider 路径在模块加载时依赖 google.genai。"""
    from pydantic_ai.models.google import GoogleModel
    from pydantic_ai.providers.google import GoogleProvider

    provider = GoogleProvider(api_key=api_key or None, base_url=base_url or None)
    settings = _google_thinking_settings(model_name)
    if settings is not None:
        return GoogleModel(model_name, provider=provider, settings=settings)  # type: ignore[arg-type]
    return GoogleModel(model_name, provider=provider)


def _build_primary(spec: str, *, api_key: str, base_url: str) -> Model:
    google_name = _google_model_name(spec)
    if google_name is not None:
        # 未直配 key 时传 None，GoogleProvider 再读 GOOGLE_API_KEY 或 GEMINI_API_KEY。
        return _build_google_model(google_name, api_key=api_key or None, base_url=base_url or None)
    if spec.startswith("openai:") and (api_key or base_url):
        # OpenAI-compatible 端点显式直配（本地模型 / 代理 / Foundry compatible）
        from pydantic_ai.models.openai import OpenAIChatModel
        from pydantic_ai.providers.openai import OpenAIProvider

        provider = OpenAIProvider(
            api_key=api_key or None,
            base_url=base_url or None,
        )
        return OpenAIChatModel(spec.removeprefix("openai:"), provider=provider)
    if spec.startswith("anthropic:") and api_key:
        return _build_anthropic(spec.removeprefix("anthropic:"), api_key=api_key, base_url=base_url)
    if spec.startswith("azure:") and (api_key or base_url):
        return _build_azure(spec.removeprefix("azure:"), api_key=api_key, base_url=base_url)
    # 其余 spec 走 Pydantic AI 原生推断，
    # 凭证由各 Provider 的原生环境变量提供。fallback 同样不走这里的直配。
    from pydantic_ai.models import infer_model

    return infer_model(spec)


def _build_anthropic(model_name: str, *, api_key: str, base_url: str) -> Model:
    from pydantic_ai.models.anthropic import AnthropicModel
    from pydantic_ai.providers.anthropic import AnthropicProvider

    return AnthropicModel(
        model_name,
        provider=AnthropicProvider(api_key=api_key, base_url=base_url or None),
    )


def _build_azure(model_name: str, *, api_key: str, base_url: str) -> Model:
    from pydantic_ai.models.openai import OpenAIChatModel
    from pydantic_ai.providers.azure import AzureProvider

    return OpenAIChatModel(
        model_name,
        provider=AzureProvider(azure_endpoint=base_url or None, api_key=api_key or None),
    )


def _build_fallbacks(settings: Settings, primary_spec: str) -> list[tuple[str, Model]]:
    """fallback 只走原生推断。凭证用各 Provider 原生环境变量，不复用主模型的 AI_PROVIDER_*，也不复用入口 apiKey。"""
    from pydantic_ai.models import infer_model

    built: list[tuple[str, Model]] = []
    for spec in _parse_specs(settings.ai_fallback_models):
        if spec == primary_spec:
            continue
        try:
            built.append((spec, infer_model(spec)))
        except Exception as exc:  # noqa: BLE001 — 单个 fallback 无效不影响主模型
            logger.warning(
                "Ignoring invalid AI_FALLBACK_MODELS entry %s: %s",
                public_model_spec(spec),
                type(exc).__name__,
                exc_info=exc,
            )
    return built


def create_model(settings: Settings) -> ModelHandle:
    if settings.agent_contract_use_test_model:
        from pydantic_ai.models.test import TestModel

        return ModelHandle(
            model=TestModel(custom_output_text="contract-test-reply"),
            spec="test:contract",
            blocked_reason=None,
        )

    spec = settings.ai_model.strip()
    if not spec:
        return ModelHandle(
            model=None,
            spec="blocked",
            blocked_reason="AI model is not configured (missing AI_MODEL).",
        )

    try:
        primary = _build_primary(
            spec,
            api_key=settings.ai_provider_api_key.strip(),
            base_url=settings.ai_provider_base_url.strip(),
        )
    except Exception as exc:  # noqa: BLE001 — 凭证/spec 错误统一映射 BLOCKED，不让服务启动失败
        logger.warning("AI model configuration failed for %s", public_model_spec(spec), exc_info=exc)
        return ModelHandle(
            model=None,
            spec=spec,
            blocked_reason=external_failure_message("AI provider is not configured or invalid", exc),
        )

    fallbacks = _build_fallbacks(settings, spec)
    return _wrap_chain(primary, spec, fallbacks)


def _recording_fallback_handler(chain_specs: tuple[str, ...]):
    """fallback_on 的异常记录器：只记日志（候选序号按触发次数推算），永不自行决定切换。

    返回 False 让后面的 ModelAPIError 类型判断决定是否切换；业务/工具异常不触发、不记录。
    """
    triggered = {"count": 0}

    def _record(exc: Exception) -> bool:
        if isinstance(exc, ModelAPIError):
            triggered["count"] += 1
            logger.warning(
                "AI model fallback triggered (event %d, %s); chain=%s",
                triggered["count"],
                type(exc).__name__,
                ", ".join(public_model_spec(item) for item in chain_specs),
            )
        return False

    return _record


def _wrap_chain(
    primary: Model, spec: str, fallbacks: list[tuple[str, Model]]
) -> ModelHandle:
    if not fallbacks:
        return ModelHandle(model=primary, spec=spec, blocked_reason=None)
    from pydantic_ai.models.fallback import FallbackModel

    fallback_specs = tuple(name for name, _ in fallbacks)
    # fallback_on 混排记录器 + ModelAPIError：仅 Provider API 错误（含 429/5xx）切换，
    # 业务/工具异常不切换（与原默认语义一致），切换事件另记 warning 供事后核查。
    return ModelHandle(
        model=FallbackModel(
            primary,
            *(model for _, model in fallbacks),
            fallback_on=[_recording_fallback_handler((spec, *fallback_specs)), ModelAPIError],
        ),
        spec=spec,
        blocked_reason=None,
        fallback_specs=fallback_specs,
    )


def _build_entry_fallbacks(
    fallbacks: list[EntryFallback] | None, primary_spec: str, seen_specs: set[str]
) -> list[tuple[str, Model]]:
    """入口备援逐个以自带凭证建模；与主用同 spec 或已在链上的跳过，建不起来记 warning 跳过。"""
    built: list[tuple[str, Model]] = []
    for entry in fallbacks or []:
        cleaned = entry.spec.strip()
        if not cleaned or cleaned == primary_spec or cleaned in seen_specs:
            continue
        try:
            model = _build_primary(
                cleaned,
                api_key=(entry.api_key or "").strip(),
                base_url=(entry.base_url or "").strip(),
            )
        except Exception as exc:  # noqa: BLE001 — 单个备援坏掉不拖垮整链，不回显密钥
            logger.warning(
                "Ignoring unbuildable AI model entry fallback %s: %s",
                public_model_spec(cleaned),
                type(exc).__name__,
                exc_info=exc,
            )
            continue
        seen_specs.add(cleaned)
        built.append((cleaned, model))
    return built


def create_model_for_entry(
    *,
    spec: str,
    api_key: str,
    base_url: str | None,
    settings: Settings | None = None,
    fallbacks: list[EntryFallback] | None = None,
) -> ModelHandle:
    """Java 模型入口。不用进程 AI_MODEL。

    入口 spec 或凭证建不起来时仍 BLOCKED，备援不得顶替（ADR-0004）。
    构建成功后依次挂：请求带入的入口备援链（各自带凭证，可混 provider），
    再挂 AI_FALLBACK_MODELS（走原生环境凭证，不使用入口 apiKey）。
    """
    cleaned = spec.strip()
    if not cleaned:
        return ModelHandle(
            model=None,
            spec="blocked",
            blocked_reason="AI model is not configured (missing AI_MODEL).",
        )
    try:
        primary = _build_primary(cleaned, api_key=api_key.strip(), base_url=(base_url or "").strip())
    except Exception as exc:  # noqa: BLE001 — 入口 spec/凭证错误映射 BLOCKED，不回显密钥
        logger.warning("AI model entry failed for %s", public_model_spec(cleaned), exc_info=exc)
        return ModelHandle(
            model=None,
            spec=cleaned,
            blocked_reason=external_failure_message("AI provider is not configured or invalid", exc),
        )
    seen_specs: set[str] = set()
    chain = _build_entry_fallbacks(fallbacks, cleaned, seen_specs)
    if settings is not None:
        for env_spec, env_model in _build_fallbacks(settings, cleaned):
            if env_spec in seen_specs:
                continue
            seen_specs.add(env_spec)
            chain.append((env_spec, env_model))
    return _wrap_chain(primary, cleaned, chain)
