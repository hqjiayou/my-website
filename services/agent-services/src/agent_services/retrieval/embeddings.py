"""Embedding 客户端：AI_EMBEDDING_MODEL spec → 向量。

当前只支持 openai: 前缀（含 OpenAI-compatible base_url）；
其它 spec → BLOCKED（诚实失败，不默认任何模型）。
请求走已声明的 httpx，不依赖未锁定的 openai SDK。
"""

from typing import Protocol

import httpx

from agent_services.config import Settings

_DEFAULT_BASE_URL = "https://api.openai.com/v1"


class EmbeddingClient(Protocol):
    async def embed(self, texts: list[str]) -> list[list[float]]: ...


class OpenAIEmbeddingClient:
    def __init__(
        self,
        model: str,
        *,
        api_key: str | None,
        base_url: str | None,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        root = (base_url or _DEFAULT_BASE_URL).rstrip("/")
        self._model = model
        self._api_key = api_key
        self._url = f"{root}/embeddings"
        self._client = client or httpx.AsyncClient(timeout=60.0)

    async def embed(self, texts: list[str]) -> list[list[float]]:
        headers: dict[str, str] = {}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        response = await self._client.post(
            self._url,
            headers=headers,
            json={"model": self._model, "input": texts},
        )
        response.raise_for_status()
        items = sorted(response.json()["data"], key=lambda item: item["index"])
        return [item["embedding"] for item in items]


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
