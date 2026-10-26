"""向量存储：agent_retrieval schema（pgvector）。

所有查询强制携带 site_id 过滤——多租户隔离是硬约束，不允许全库检索。
"""

import asyncio
import json
import logging
from dataclasses import dataclass
from typing import Protocol

from agent_services.retrieval.embeddings import _finite_floats

logger = logging.getLogger(__name__)

# pgvector 的 vector 型 HNSW 索引上限。超过则不建索引，搜索仍走顺序扫描。
HNSW_MAX_DIMENSIONS = 2000


@dataclass(frozen=True)
class RetrievedChunk:
    source_id: str
    chunk_index: int
    content: str
    score: float


CITATION_LIMIT = 5


def citation_sources(
    chunks: list[RetrievedChunk], *, limit: int = CITATION_LIMIT
) -> list[dict[str, object]]:
    """同一 (source_id, chunk_index) 只留最高分，再按 score 降序取前 limit 条。"""
    chosen: dict[tuple[str, int], RetrievedChunk] = {}
    for chunk in chunks:
        key = (chunk.source_id, chunk.chunk_index)
        previous = chosen.get(key)
        if previous is None or chunk.score > previous.score:
            chosen[key] = chunk
    ranked = sorted(chosen.values(), key=lambda item: (-item.score, item.source_id, item.chunk_index))
    return [
        {"sourceId": item.source_id, "chunkIndex": item.chunk_index, "score": item.score}
        for item in ranked[:limit]
    ]


def hnsw_index_allowed(dimensions: int) -> bool:
    """维度不超过 pgvector HNSW 上限时才建索引。"""
    return dimensions <= HNSW_MAX_DIMENSIONS


def like_literal_pattern(token: str) -> str:
    """收成 ILIKE 字面量模式。%、_、\\ 按字面匹配，不当成通配符。"""
    escaped = token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def lexical_search_statement(
    site_id: str, tokens: list[str], limit: int
) -> tuple[str, list[object]]:
    """词法 SQL 只按调用方 site_id 过滤，token 只进绑定参数。

    LIMIT 之前按命中 token 数降序，同分再按 source_id、chunk_index。
    否则更宽的短 token 会占满候选窗口，把型号整段命中挤掉。
    """
    arguments: list[object] = [site_id]
    clauses: list[str] = []
    for token in tokens:
        arguments.append(like_literal_pattern(token))
        clauses.append(f"content ILIKE ${len(arguments)} ESCAPE '\\'")
    rank_terms = [
        f"(content ILIKE ${index} ESCAPE '\\')::int" for index in range(2, len(arguments) + 1)
    ]
    arguments.append(limit)
    sql = (
        "SELECT source_id, chunk_index, content, 0::float8 AS score "
        "FROM agent_retrieval.chunks "
        f"WHERE site_id = $1 AND ({' OR '.join(clauses)}) "
        f"ORDER BY ({' + '.join(rank_terms)}) DESC, source_id, chunk_index "
        f"LIMIT ${len(arguments)}"
    )
    return sql, arguments


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

    async def search_lexical(
        self, *, site_id: str, tokens: list[str], limit: int
    ) -> list[RetrievedChunk]: ...

    async def close(self) -> None: ...


class PgVectorRetrievalStore:
    """pgvector 实现。schema/表首次使用时创建；CREATE EXTENSION 需要 DBA 预授权，
    失败时假定扩展已由运维预建（部署文档约定）。"""

    def __init__(self, dsn: str, *, dimensions: int, hnsw_ef_search: int = 40) -> None:
        self._dsn = dsn
        self._dimensions = dimensions
        self._hnsw_ef_search = hnsw_ef_search
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
            if hnsw_index_allowed(self._dimensions):
                await conn.execute(
                    "CREATE INDEX IF NOT EXISTS idx_agent_retrieval_chunks_embedding_hnsw "
                    "ON agent_retrieval.chunks USING hnsw (embedding vector_cosine_ops) "
                    "WITH (m = 16, ef_construction = 64)"
                )
            else:
                logger.warning(
                    "Skipping HNSW index: embedding dimensions %s exceed %s; "
                    "vector search will scan",
                    self._dimensions,
                    HNSW_MAX_DIMENSIONS,
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
            # 同一文件的删除与插入都在这个事务里。锁键是 site_id:source_id 的 64-bit hash，
            # 只串行化这一份文件；事务提交或回滚时 pg_advisory_xact_lock 自动释放，其它 source 不互相阻塞。
            # 本机单测没有 pgvector，不会执行到这里。
            await conn.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))",
                f"{site_id}:{source_id}",
            )
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
        # hnsw.ef_search 是事务局部参数。asyncpg 必须把 set_config 和查询放进同一个 transaction。
        async with pool.acquire() as conn, conn.transaction():
            if (
                isinstance(self._hnsw_ef_search, int)
                and not isinstance(self._hnsw_ef_search, bool)
                and self._hnsw_ef_search > 0
            ):
                await conn.execute(
                    "SELECT set_config('hnsw.ef_search', $1, true)",
                    str(self._hnsw_ef_search),
                )
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

    async def search_lexical(
        self, *, site_id: str, tokens: list[str], limit: int
    ) -> list[RetrievedChunk]:
        """按调用方 site_id 做字面量子串匹配。分数由服务层覆盖率决定，这里不取向量分。"""
        if not tokens or limit < 1:
            return []
        pool = await self._get_pool()
        sql, arguments = lexical_search_statement(site_id, tokens, limit)
        async with pool.acquire() as conn:
            rows = await conn.fetch(sql, *arguments)
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
