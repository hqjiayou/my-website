"""向量存储：agent_retrieval schema（pgvector）。

所有查询强制携带 site_id 过滤——多租户隔离是硬约束，不允许全库检索。
"""

import asyncio
import json
from dataclasses import dataclass
from typing import Protocol

from agent_services.retrieval.embeddings import _finite_floats


@dataclass(frozen=True)
class RetrievedChunk:
    source_id: str
    chunk_index: int
    content: str
    score: float


class RetrievalStore(Protocol):
    async def upsert_document(
        self,
        *,
        site_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
    ) -> int: ...

    async def delete_document(self, *, site_id: str, source_id: str) -> None: ...

    async def search(
        self, *, site_id: str, embedding: list[float], top_k: int
    ) -> list[RetrievedChunk]: ...

    async def close(self) -> None: ...


class PgVectorRetrievalStore:
    """pgvector 实现。schema/表首次使用时创建；CREATE EXTENSION 需要 DBA 预授权，
    失败时假定扩展已由运维预建（部署文档约定）。"""

    def __init__(self, dsn: str, *, dimensions: int) -> None:
        self._dsn = dsn
        self._dimensions = dimensions
        self._pool = None
        self._pool_lock = asyncio.Lock()

    async def _get_pool(self):
        if self._pool is not None:
            return self._pool
        async with self._pool_lock:
            if self._pool is not None:
                return self._pool
            import asyncpg

            pool = await asyncpg.create_pool(self._dsn, min_size=1, max_size=5)
            self._pool = pool
            try:
                await self._ensure_schema()
            except Exception:
                self._pool = None
                await pool.close()
                raise
        return self._pool

    async def _ensure_schema(self) -> None:
        assert self._pool is not None
        async with self._pool.acquire() as conn:
            try:
                await conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
            except Exception:  # noqa: BLE001 — 无扩展安装权限时由运维预建
                pass
            await conn.execute("CREATE SCHEMA IF NOT EXISTS agent_retrieval")
            await conn.execute(
                f"""
                CREATE TABLE IF NOT EXISTS agent_retrieval.chunks (
                    id          BIGSERIAL PRIMARY KEY,
                    site_id     TEXT NOT NULL,
                    source_id   TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content     TEXT NOT NULL,
                    embedding   vector({self._dimensions}) NOT NULL,
                    metadata    JSONB NOT NULL DEFAULT '{{}}'::jsonb,
                    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """
            )
            await conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_agent_retrieval_chunks_site_source "
                "ON agent_retrieval.chunks (site_id, source_id)"
            )

    def _vector_literal(self, embedding: list[float]) -> str:
        if len(embedding) != self._dimensions:
            raise ValueError(
                f"Embedding dimension {len(embedding)} does not match configured {self._dimensions}."
            )
        return "[" + ",".join(repr(value) for value in _finite_floats(embedding)) + "]"

    async def upsert_document(
        self,
        *,
        site_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
    ) -> int:
        pool = await self._get_pool()
        metadata = json.dumps({"title": title})
        async with pool.acquire() as conn, conn.transaction():
            await conn.execute(
                "DELETE FROM agent_retrieval.chunks WHERE site_id = $1 AND source_id = $2",
                site_id,
                source_id,
            )
            for index, (content, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
                await conn.execute(
                    "INSERT INTO agent_retrieval.chunks "
                    "(site_id, source_id, chunk_index, content, embedding, metadata) "
                    "VALUES ($1, $2, $3, $4, $5::vector, $6::jsonb)",
                    site_id,
                    source_id,
                    index,
                    content,
                    self._vector_literal(embedding),
                    metadata,
                )
        return len(chunks)

    async def delete_document(self, *, site_id: str, source_id: str) -> None:
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            await conn.execute(
                "DELETE FROM agent_retrieval.chunks WHERE site_id = $1 AND source_id = $2",
                site_id,
                source_id,
            )

    async def search(
        self, *, site_id: str, embedding: list[float], top_k: int
    ) -> list[RetrievedChunk]:
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            rows = await conn.fetch(
                "SELECT source_id, chunk_index, content, "
                "1 - (embedding <=> $2::vector) AS score "
                "FROM agent_retrieval.chunks "
                "WHERE site_id = $1 "
                "ORDER BY embedding <=> $2::vector LIMIT $3",
                site_id,
                self._vector_literal(embedding),
                top_k,
            )
        return [
            RetrievedChunk(
                source_id=row["source_id"],
                chunk_index=row["chunk_index"],
                content=row["content"],
                score=float(row["score"]),
            )
            for row in rows
        ]

    async def close(self) -> None:
        async with self._pool_lock:
            if self._pool is not None:
                await self._pool.close()
                self._pool = None
