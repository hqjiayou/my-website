"""检索服务：分块 → embedding → 向量存取；site_id 全链路强制。"""

import logging

from agent_services.config import Settings
from agent_services.retrieval.chunking import split_text
from agent_services.retrieval.embeddings import EmbeddingClient, create_embedding_client
from agent_services.retrieval.store import RetrievalStore, RetrievedChunk

logger = logging.getLogger(__name__)

DEFAULT_TOP_K = 4


class RetrievalService:
    def __init__(self, embedder: EmbeddingClient, store: RetrievalStore) -> None:
        self._embedder = embedder
        self._store = store

    async def upsert_document(self, *, site_id: str, source_id: str, title: str, content: str) -> int:
        chunks = split_text(content)
        if not chunks:
            await self._store.delete_document(site_id=site_id, source_id=source_id)
            return 0
        embeddings = await self._embedder.embed(chunks)
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
        embeddings = await self._embedder.embed([query])
        return await self._store.search(site_id=site_id, embedding=embeddings[0], top_k=top_k)

    async def retrieve_context(self, *, site_id: str, query: str) -> list[str]:
        """chat 检索增强：失败降级为无上下文（派生索引问题不阻断对话），记录日志。"""
        try:
            chunks = await self.search(site_id=site_id, query=query)
        except Exception:  # noqa: BLE001
            logger.warning("retrieval search failed; continuing without context", exc_info=True)
            return []
        return [chunk.content for chunk in chunks]

    async def close(self) -> None:
        await self._store.close()


def create_retrieval_service(settings: Settings) -> tuple[RetrievalService | None, str | None]:
    """返回 (service, blocked_reason)；两者互斥。"""
    dsn = settings.retrieval_database_url.strip()
    if not dsn:
        return None, "Retrieval database is not configured (missing RETRIEVAL_DATABASE_URL)."
    embedder, blocked_reason = create_embedding_client(settings)
    if embedder is None:
        return None, blocked_reason

    from agent_services.retrieval.store import PgVectorRetrievalStore

    store = PgVectorRetrievalStore(dsn, dimensions=settings.ai_embedding_dimensions)
    return RetrievalService(embedder, store), None
