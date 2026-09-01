"""AI_MODEL spec → Pydantic AI Model。

不自建 Provider 抽象层：Model/Provider 两层抽象直接复用 Pydantic AI（设计 V1 §2）。
无配置 / 凭证缺失 → BLOCKED（ADR-0004），不默认任何模型。
"""

from dataclasses import dataclass

from pydantic_ai.models import Model

from agent_services.config import Settings


@dataclass(frozen=True)
class ModelHandle:
    model: Model | None
    spec: str
    blocked_reason: str | None

    @property
    def live(self) -> bool:
        return self.model is not None


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
        api_key = settings.ai_provider_api_key.strip()
        base_url = settings.ai_provider_base_url.strip()
        if spec.startswith("openai:") and (api_key or base_url):
            # OpenAI-compatible 端点显式直配（本地模型 / 代理 / Foundry compatible）
            from pydantic_ai.models.openai import OpenAIChatModel
            from pydantic_ai.providers.openai import OpenAIProvider

            provider = OpenAIProvider(
                api_key=api_key or None,
                base_url=base_url or None,
            )
            model: Model = OpenAIChatModel(spec.removeprefix("openai:"), provider=provider)
        else:
            # 其余 spec（azure:/anthropic:/google:/...）走 Pydantic AI 原生推断，
            # 凭证由各 Provider 的原生环境变量提供
            from pydantic_ai.models import infer_model

            model = infer_model(spec)
        return ModelHandle(model=model, spec=spec, blocked_reason=None)
    except Exception as exc:  # noqa: BLE001 — 凭证/spec 错误统一映射 BLOCKED，不让服务启动失败
        return ModelHandle(
            model=None,
            spec=spec,
            blocked_reason=f"AI provider is not configured or invalid: {exc}",
        )
