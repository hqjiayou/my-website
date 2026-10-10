"""Embedding 客户端：AI_EMBEDDING_MODEL spec → 向量。

当前只支持 openai: 前缀（含 OpenAI-compatible base_url）；
其它 spec → BLOCKED（诚实失败，不默认任何模型）。
请求走已声明的 httpx，不依赖未锁定的 openai SDK。
"""

import math
from typing import Protocol

import httpx

from agent_services.config import Settings

_DEFAULT_BASE_URL = "https://api.openai.com/v1"
# OpenAI embeddings 单次 input 上限是 2048；按块发送，避免长文档一次请求被拒。
_EMBED_BATCH_SIZE = 128


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
        batch_size: int = _EMBED_BATCH_SIZE,
    ) -> None:
        root = (base_url or _DEFAULT_BASE_URL).rstrip("/")
        self._model = model
        self._api_key = api_key
        self._url = f"{root}/embeddings"
        self._owns_client = client is None
        self._client = client or httpx.AsyncClient(timeout=60.0)
        self._batch_size = batch_size if batch_size > 0 else _EMBED_BATCH_SIZE

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    async def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        vectors: list[list[float]] = []
        for start in range(0, len(texts), self._batch_size):
            vectors.extend(await self._embed_batch(texts[start : start + self._batch_size]))
        return vectors

    async def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        headers: dict[str, str] = {}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        response = await self._client.post(
            self._url,
            headers=headers,
            json={"model": self._model, "input": texts},
        )
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data") if isinstance(payload, dict) else None
        if not isinstance(data, list):
            raise ValueError("Embedding response is missing data.")

        def item_index(item: object) -> int:
            if not isinstance(item, dict) or isinstance(item.get("index"), bool) or not isinstance(item.get("index"), int):
                raise ValueError("Embedding response items are incomplete.")
            return item["index"]

        items = sorted(data, key=item_index)
        if len(items) != len(texts):
            raise ValueError(
                f"Embedding response count {len(items)} does not match input count {len(texts)}."
            )
        vectors: list[list[float]] = []
        for expected, item in enumerate(items):
            embedding = item.get("embedding")
            if item.get("index") != expected or not isinstance(embedding, list):
                raise ValueError("Embedding response items are incomplete.")
            vectors.append(_finite_floats(embedding))
        return vectors


def _finite_floats(values: object) -> list[float]:
    if not isinstance(values, list) or any(
        isinstance(value, bool)
        or not isinstance(value, (int, float))
        or not math.isfinite(float(value))
        for value in values
    ):
        raise ValueError("Embedding values must be numbers.")
    return [float(value) for value in values]


def create_embedding_client(settings: Settings) -> tuple[EmbeddingClient | None, str | None]:
    """返回 (client, blocked_reason)；两者互斥。"""
    spec = settings.ai_embedding_model.strip()
    if not spec:
        return None, "Embedding model is not configured (missing AI_EMBEDDING_MODEL)."
    if not spec.startswith("openai:"):
        # spec 可能是带账号的 URL；健康检查会把这段原因展示到管理端
        if "://" in spec or "@" in spec:
            return None, "Unsupported embedding model spec (only 'openai:' is supported)."
        return None, f"Unsupported embedding model spec: {spec} (only 'openai:' is supported)."
    api_key = settings.ai_provider_api_key.strip() or None
    base_url = settings.ai_provider_base_url.strip() or None
    if not api_key and not base_url:
        return None, "Embedding provider credentials are not configured."
    return (
        OpenAIEmbeddingClient(spec.removeprefix("openai:"), api_key=api_key, base_url=base_url),
        None,
    )
