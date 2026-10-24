"""检索服务：分块 → embedding → 向量存取；site_id 全链路强制。"""

import logging

from agent_services.config import Settings
from agent_services.retrieval.chunking import split_text
from agent_services.retrieval.embeddings import EmbeddingClient, create_embedding_client
from agent_services.retrieval.store import RetrievalStore, RetrievedChunk

logger = logging.getLogger(__name__)

DEFAULT_TOP_K = 4
MAX_UPSERT_CHUNKS = 256


class RetrievalChunkLimitExceeded(Exception):
    """单文档分块数超过护栏。这是请求问题，由 API 映射为 400，不是外部依赖故障。"""


class RetrievalService:
    def __init__(self, embedder: EmbeddingClient, store: RetrievalStore) -> None:
        self._embedder = embedder
        self._store = store
        self._degraded = False
        self._last_error: str | None = None

    @property
    def degraded(self) -> bool:
        return self._degraded

    @property
    def last_error(self) -> str | None:
        return self._last_error

    async def upsert_document(self, *, site_id: str, source_id: str, title: str, content: str) -> int:
        chunks = split_text(content)
        if not chunks:
            await self._store.delete_document(site_id=site_id, source_id=source_id)
            return 0
        if len(chunks) > MAX_UPSERT_CHUNKS:
            raise RetrievalChunkLimitExceeded(
                f"Document exceeds the maximum of {MAX_UPSERT_CHUNKS} chunks."
            )
        embeddings = await self._embedder.embed_documents(chunks)
        return await self._store.upsert_document(
            site_id=site_id,
            source_id=source_id,
            title=title,
            chunks=chunks,
            embeddings=embeddings,
        )

    async def delete_document(self, *, site_id: str, source_id: str) -> None:
        await self._store.delete_document(site_id=site_id, source_id=source_id)

    async def search(self, *, site_id: str, query: str, top_k: int = DEFAULT_TOP_K) -> list[RetrievedChunk]:
        embedding = await self._embedder.embed_query(query)
        return await self._store.search(site_id=site_id, embedding=embedding, top_k=top_k)

    async def retrieve_chunks(
        self, *, site_id: str, query: str, top_k: int = DEFAULT_TOP_K
    ) -> list[RetrievedChunk]:
        """与 retrieve_context 同一条降级路径：嵌入或向量库失败时返回空列表并置 degraded。

        chat grounding 与 POST /retrieval/search 都走这里，避免另写一套错误格式。
        """
        try:
            chunks = await self.search(site_id=site_id, query=query, top_k=top_k)
        except Exception as exc:  # noqa: BLE001
            self._degraded = True
            self._last_error = type(exc).__name__
            logger.warning("retrieval search failed; continuing without context", exc_info=True)
            return []
        self._degraded = False
        self._last_error = None
        return chunks

    async def retrieve_context(self, *, site_id: str, query: str) -> list[str]:
        """chat 检索增强：失败降级为无上下文（派生索引问题不阻断对话），记录日志。

        最近一次失败置 degraded 并记下异常类型名；成功则清除。返回值仍是上下文列表。
        """
        chunks = await self.retrieve_chunks(site_id=site_id, query=query)
        return [chunk.content for chunk in chunks]

    async def close(self) -> None:
        closer = getattr(self._embedder, "aclose", None)
        if closer is not None:
            await closer()
        await self._store.close()


def create_retrieval_service(settings: Settings) -> tuple[RetrievalService | None, str | None]:
    """返回 (service, blocked_reason)；两者互斥。"""
    dsn = settings.retrieval_database_url.strip()
    if not dsn:
        return None, "Retrieval database is not configured (missing RETRIEVAL_DATABASE_URL)."
    if settings.ai_embedding_dimensions < 1:
        return None, "AI_EMBEDDING_DIMENSIONS must be a positive integer."
    embedder, blocked_reason = create_embedding_client(settings)
    if embedder is None:
        return None, blocked_reason

    from agent_services.retrieval.store import PgVectorRetrievalStore

    store = PgVectorRetrievalStore(dsn, dimensions=settings.ai_embedding_dimensions)
    return RetrievalService(embedder, store), None
