"""Embedding 客户端：AI_EMBEDDING_MODEL spec → 向量。

支持 openai:、google:，以及旧别名 google-gla:。
直配凭证用 AI_PROVIDER_API_KEY / AI_PROVIDER_BASE_URL。
Google 未直配 key 时由 Provider 读取 GOOGLE_API_KEY 或 GEMINI_API_KEY。
其它 spec → BLOCKED（诚实失败，不默认任何模型）。
OpenAI 请求走已声明的 httpx。Google 走 Pydantic AI Embedder，不自写 Gemini REST。
"""

import logging
import math
from collections.abc import Sequence
from typing import Protocol

import httpx

from agent_services.api.errors import external_failure_message
from agent_services.config import Settings

logger = logging.getLogger(__name__)

_DEFAULT_BASE_URL = "https://api.openai.com/v1"
# OpenAI embeddings 单次 input 上限是 2048；按块发送，避免长文档一次请求被拒。
_EMBED_BATCH_SIZE = 128


class EmbeddingClient(Protocol):
    async def embed(self, texts: list[str]) -> list[list[float]]: ...

    async def embed_documents(self, texts: list[str]) -> list[list[float]]: ...

    async def embed_query(self, text: str) -> list[float]: ...


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

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return await self.embed(texts)

    async def embed_query(self, text: str) -> list[float]:
        vectors = await self.embed([text])
        if len(vectors) != 1:
            raise ValueError(f"Embedding response count {len(vectors)} does not match input count 1.")
        return vectors[0]


class GoogleEmbeddingClient:
    """Gemini 嵌入。文件走 embed_documents，查询走 embed_query，才会带上对应 task type。"""

    def __init__(
        self,
        model: str,
        *,
        api_key: str | None,
        base_url: str | None,
        dimensions: int,
    ) -> None:
        from pydantic_ai.embeddings import Embedder
        from pydantic_ai.embeddings.google import GoogleEmbeddingModel
        from pydantic_ai.providers.google import GoogleProvider

        provider = GoogleProvider(api_key=api_key or None, base_url=base_url or None)
        embedding_model = GoogleEmbeddingModel(model, provider=provider)
        self._embedder = Embedder(embedding_model, settings={"dimensions": dimensions})

    async def aclose(self) -> None:
        model = getattr(self._embedder, "model", None)
        provider = getattr(model, "_provider", None)
        http_client = getattr(provider, "_own_http_client", None)
        close = getattr(http_client, "aclose", None)
        if close is not None:
            await close()

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return await self.embed_documents(texts)

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        result = await self._embedder.embed_documents(texts)
        return _embedding_vectors(result.embeddings, expected=len(texts))

    async def embed_query(self, text: str) -> list[float]:
        result = await self._embedder.embed_query(text)
        return _embedding_vectors(result.embeddings, expected=1)[0]


def _embedding_vectors(raw: object, *, expected: int) -> list[list[float]]:
    if isinstance(raw, (str, bytes)) or not isinstance(raw, Sequence):
        raise ValueError("Embedding response is missing data.")
    if len(raw) != expected:
        raise ValueError(f"Embedding response count {len(raw)} does not match input count {expected}.")
    vectors: list[list[float]] = []
    for item in raw:
        if isinstance(item, list):
            vectors.append(_finite_floats(item))
        elif isinstance(item, (str, bytes)) or not isinstance(item, Sequence):
            raise ValueError("Embedding values must be numbers.")
        else:
            vectors.append(_finite_floats(list(item)))
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


_EMBEDDING_SPEC_HINT = "only 'openai:', 'google:', and 'google-gla:' are supported"
# 一次检索最多登记的嵌入模型类。同一模型名算一类，避免按类数线性多嵌问题。
MAX_EMBEDDING_CLASSES = 4


def embedding_class_name(spec: str) -> str:
    """分类键是嵌入模型名。`google-gla:` 与 `google:` 写入同一类。"""
    text = spec.strip()
    if text.startswith("google-gla:"):
        return "google:" + text.removeprefix("google-gla:")
    return text


def limit_embedding_class_specs(items: list | None) -> list | None:
    """按模型名去重，超过上限则拒绝。空列表原样返回。"""
    if not items:
        return items
    chosen: list = []
    seen: set[str] = set()
    for item in items:
        spec = getattr(item, "embedding_spec", None)
        if not isinstance(spec, str):
            raise ValueError("embeddingClasses entries require embeddingSpec.")
        name = embedding_class_name(spec)
        if name in seen:
            continue
        seen.add(name)
        chosen.append(item)
    if len(chosen) > MAX_EMBEDDING_CLASSES:
        raise ValueError(
            f"embeddingClasses accepts at most {MAX_EMBEDDING_CLASSES} classes."
        )
    return chosen


def _google_embedding_model_name(spec: str) -> str | None:
    for prefix in ("google-gla:", "google:"):
        if spec.startswith(prefix):
            return spec.removeprefix(prefix)
    return None


def create_embedding_client(settings: Settings) -> tuple[EmbeddingClient | None, str | None]:
    """返回 (client, blocked_reason)；两者互斥。"""
    spec = settings.ai_embedding_model
    api_key = settings.ai_provider_api_key
    if _google_embedding_model_name(spec.strip()) and not api_key.strip():
        api_key = settings.gemini_api_key
    return create_embedding_client_for(
        spec=spec,
        api_key=api_key,
        base_url=settings.ai_provider_base_url,
        dimensions=settings.ai_embedding_dimensions,
    )


def create_embedding_client_for(
    *,
    spec: str,
    api_key: str | None,
    base_url: str | None,
    dimensions: int,
) -> tuple[EmbeddingClient | None, str | None]:
    """用显式 spec / key 构造嵌入客户端。空 spec → 缺 AI_EMBEDDING_MODEL。"""
    spec = spec.strip()
    if not spec:
        return None, "Embedding model is not configured (missing AI_EMBEDDING_MODEL)."
    api_key = (api_key or "").strip() or None
    base_url = (base_url or "").strip() or None
    if spec.startswith("openai:"):
        if not api_key and not base_url:
            return None, "Embedding provider credentials are not configured."
        return (
            OpenAIEmbeddingClient(spec.removeprefix("openai:"), api_key=api_key, base_url=base_url),
            None,
        )
    google_name = _google_embedding_model_name(spec)
    if google_name is not None:
        # 没有直配 key / base URL 时仍尝试构造：部署环境可能有 GOOGLE_API_KEY 或 GEMINI_API_KEY。
        # Provider 在构造当下缺钥会抛错，这里收成不含密钥正文的 BLOCKED。
        try:
            return (
                GoogleEmbeddingClient(
                    google_name,
                    api_key=api_key,
                    base_url=base_url,
                    dimensions=dimensions,
                ),
                None,
            )
        except Exception as exc:  # noqa: BLE001 — 凭证/spec 错误统一映射 BLOCKED
            visible = "redacted" if "://" in spec or "@" in spec else spec
            logger.warning(
                "Embedding model configuration failed for %s: %s",
                visible,
                type(exc).__name__,
            )
            return None, external_failure_message("Embedding provider is not configured or invalid", exc)
    # spec 可能是带账号的 URL；健康检查会把这段原因展示到管理端
    if "://" in spec or "@" in spec:
        return None, f"Unsupported embedding model spec ({_EMBEDDING_SPEC_HINT})."
    return None, f"Unsupported embedding model spec: {spec} ({_EMBEDDING_SPEC_HINT})."
