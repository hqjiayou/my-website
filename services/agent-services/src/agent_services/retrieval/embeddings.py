"""Embedding 客户端：AI_EMBEDDING_MODEL spec → 向量。

当前只支持 openai: 前缀（含 OpenAI-compatible base_url）；
其它 spec → BLOCKED（诚实失败，不默认任何模型）。
"""

from typing import Protocol

from agent_services.config import Settings


class EmbeddingClient(Protocol):
    async def embed(self, texts: list[str]) -> list[list[float]]: ...


class OpenAIEmbeddingClient:
    def __init__(self, model: str, *, api_key: str | None, base_url: str | None) -> None:
        from openai import AsyncOpenAI

        self._model = model
        self._client = AsyncOpenAI(api_key=api_key, base_url=base_url)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        response = await self._client.embeddings.create(model=self._model, input=texts)
        return [item.embedding for item in response.data]


def create_embedding_client(settings: Settings) -> tuple[EmbeddingClient | None, str | None]:
    """返回 (client, blocked_reason)；两者互斥。"""
    spec = settings.ai_embedding_model.strip()
    if not spec:
        return None, "Embedding model is not configured (missing AI_EMBEDDING_MODEL)."
    if not spec.startswith("openai:"):
        return None, f"Unsupported embedding model spec: {spec} (only 'openai:' is supported)."
    api_key = settings.ai_provider_api_key.strip() or None
    base_url = settings.ai_provider_base_url.strip() or None
    if not api_key and not base_url:
        return None, "Embedding provider credentials are not configured."
    return (
        OpenAIEmbeddingClient(spec.removeprefix("openai:"), api_key=api_key, base_url=base_url),
        None,
    )
