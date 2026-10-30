"""向量存储：agent_retrieval schema（pgvector）。

所有查询强制携带 agent_id 过滤——多租户隔离是硬约束，不允许全库检索。
"""

import asyncio
import json
import logging
import re
from dataclasses import dataclass
from typing import Protocol

from agent_services.retrieval.embeddings import _finite_floats, embedding_class_name

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


def _positive_dimensions(dimensions: int) -> int:
    if isinstance(dimensions, bool) or not isinstance(dimensions, int) or dimensions < 1:
        raise ValueError("Embedding dimensions must be a positive integer.")
    return dimensions


# pgvector 把 vector(N) 的 N 存在 atttypmod；无此列或未限定维度时没有正整数。
_EMBEDDING_ATTTYPMOD_SQL = """
SELECT a.atttypmod
FROM pg_catalog.pg_attribute a
JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'agent_retrieval'
  AND c.relname = 'chunks'
  AND a.attname = 'embedding'
  AND NOT a.attisdropped
"""


# 重建遗留表时只删这一张。其它维度的类表（chunks_dN）不在这条语句里。
LEGACY_CHUNKS_DROP_SQL = "DROP TABLE IF EXISTS agent_retrieval.chunks"
_CHUNKS_TABLE_NAME = re.compile(r"^chunks(?:_d[1-9][0-9]{0,4})?$")


def qualified_chunks_table(table: str) -> str:
    """表名只接受 chunks 或 chunks_d<维度>，避免把调用方字符串拼进 SQL。"""
    if not _CHUNKS_TABLE_NAME.fullmatch(table):
        raise ValueError("Invalid chunks table name.")
    return f"agent_retrieval.{table}"


def dimension_table_name(dimensions: int, *, legacy_dimensions: int | None) -> str:
    """与遗留列同维度的类共用 chunks。维度不同则另表，不替换遗留表。"""
    dimensions = _positive_dimensions(dimensions)
    if legacy_dimensions is None or dimensions == legacy_dimensions:
        return "chunks"
    return f"chunks_d{dimensions}"


def create_class_table_sql(table: str, dimensions: int) -> str:
    """新建一类的表。只有 CREATE IF NOT EXISTS，不删除已有类。"""
    dimensions = _positive_dimensions(dimensions)
    qualified = qualified_chunks_table(table)
    return (
        f"CREATE TABLE IF NOT EXISTS {qualified} ("
        "id BIGSERIAL PRIMARY KEY, "
        "agent_id TEXT NOT NULL, "
        "source_id TEXT NOT NULL, "
        "chunk_index INTEGER NOT NULL, "
        "content TEXT NOT NULL, "
        "embedding_model TEXT NOT NULL, "
        f"embedding vector({dimensions}) NOT NULL, "
        "metadata JSONB NOT NULL DEFAULT '{}'::jsonb, "
        "created_at TIMESTAMPTZ NOT NULL DEFAULT now())"
    )


def class_row_delete_sql(table: str, *, include_legacy_nulls: bool = False) -> str:
    """只删这一类里这一份文档。include_legacy_nulls 仅用于把未标注的存量行算进进程模型。"""
    qualified = qualified_chunks_table(table)
    if include_legacy_nulls:
        predicate = "(embedding_model = $3 OR embedding_model IS NULL)"
    else:
        predicate = "embedding_model = $3"
    return (
        f"DELETE FROM {qualified} WHERE agent_id = $1 AND source_id = $2 AND {predicate}"
    )


def _class_index_names(table: str) -> tuple[str, str]:
    """遗留表沿用原来的索引名。新维度表用自己的名字，避免和 chunks 冲突。"""
    qualified_chunks_table(table)
    if table == "chunks":
        return (
            "idx_agent_retrieval_chunks_agent_source",
            "idx_agent_retrieval_chunks_embedding_hnsw",
        )
    return (
        f"idx_agent_retrieval_{table}_agent_source",
        f"idx_agent_retrieval_{table}_embedding_hnsw",
    )


def vector_search_sql(table: str, *, include_legacy_nulls: bool = False) -> str:
    """类内余弦。WHERE 带模型名，不同模型不进同一次距离计算。"""
    qualified = qualified_chunks_table(table)
    if include_legacy_nulls:
        predicate = "(embedding_model = $3 OR embedding_model IS NULL)"
    else:
        predicate = "embedding_model = $3"
    return (
        "SELECT source_id, chunk_index, content, "
        "1 - (embedding <=> $2::vector) AS score "
        f"FROM {qualified} "
        f"WHERE agent_id = $1 AND {predicate} "
        "ORDER BY embedding <=> $2::vector LIMIT $4"
    )


def like_literal_pattern(token: str) -> str:
    """收成 ILIKE 字面量模式。%、_、\\ 按字面匹配，不当成通配符。"""
    escaped = token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def lexical_search_statement(
    agent_id: str,
    tokens: list[str],
    limit: int,
    table: str = "chunks",
    embedding_models: list[str] | None = None,
    include_legacy_nulls: bool = False,
) -> tuple[str, list[object]]:
    """词法 SQL 只按调用方 agent_id 过滤，token 和模型名只进绑定参数。

    LIMIT 之前按命中 token 数降序，同分再按 source_id、chunk_index。
    否则更宽的短 token 会占满候选窗口，把型号整段命中挤掉。
    embedding_models 给出时只查这些类；未给出时保持旧的整表字面查询。
    """
    arguments: list[object] = [agent_id]
    clauses: list[str] = []
    for token in tokens:
        arguments.append(like_literal_pattern(token))
        clauses.append(f"content ILIKE ${len(arguments)} ESCAPE '\\'")
    rank_terms = [
        f"(content ILIKE ${index} ESCAPE '\\')::int" for index in range(2, len(arguments) + 1)
    ]
    model_sql = ""
    if embedding_models is not None:
        arguments.append(list(embedding_models))
        model_param = len(arguments)
        if include_legacy_nulls:
            model_sql = (
                f" AND (embedding_model = ANY(${model_param}::text[]) "
                "OR embedding_model IS NULL)"
            )
        else:
            model_sql = f" AND embedding_model = ANY(${model_param}::text[])"
    arguments.append(limit)
    rank_expr = " + ".join(rank_terms)
    sql = (
        "SELECT source_id, chunk_index, content, "
        f"({rank_expr})::float8 AS score "
        f"FROM {qualified_chunks_table(table)} "
        f"WHERE agent_id = $1 AND ({' OR '.join(clauses)}){model_sql} "
        f"ORDER BY ({rank_expr}) DESC, source_id, chunk_index "
        f"LIMIT ${len(arguments)}"
    )
    return sql, arguments


def merge_lexical_candidates(
    groups: list[list[RetrievedChunk]], *, limit: int
) -> list[RetrievedChunk]:
    """每张类表各自带了 LIMIT。按命中分数合并后再截断，表名靠前的那张不能占满窗口。"""
    if limit < 1:
        return []
    chosen: dict[tuple[str, int], RetrievedChunk] = {}
    for group in groups:
        for chunk in group:
            key = (chunk.source_id, chunk.chunk_index)
            previous = chosen.get(key)
            if previous is not None and previous.score >= chunk.score:
                continue
            chosen[key] = chunk
    ranked = sorted(
        chosen.values(),
        key=lambda item: (-item.score, item.source_id, item.chunk_index),
    )
    return ranked[:limit]


class RetrievalStore(Protocol):
    async def upsert_document(
        self,
        *,
        agent_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
        embedding_model: str = "",
    ) -> int: ...

    async def delete_document(self, *, agent_id: str, source_id: str) -> None: ...

    async def search(
        self,
        *,
        agent_id: str,
        embedding: list[float],
        top_k: int,
        embedding_model: str = "",
    ) -> list[RetrievedChunk]: ...

    async def search_lexical(
        self,
        *,
        agent_id: str,
        tokens: list[str],
        limit: int,
        embedding_models: list[str] | None = None,
    ) -> list[RetrievedChunk]: ...

    async def current_dimensions(self) -> int | None: ...

    async def ensure_schema(self, dimensions: int) -> None: ...

    async def recreate_schema(self, new_dimensions: int) -> None: ...

    async def close(self) -> None: ...


class PgVectorRetrievalStore:
    """pgvector 实现。schema/表首次使用时创建；CREATE EXTENSION 需要 DBA 预授权，
    失败时假定扩展已由运维预建（部署文档约定）。"""

    def __init__(
        self,
        dsn: str,
        *,
        dimensions: int,
        hnsw_ef_search: int = 40,
        legacy_embedding_model: str = "",
    ) -> None:
        self._dsn = dsn
        self._dimensions = dimensions
        self._hnsw_ef_search = hnsw_ef_search
        # 存量行在加列时记成进程嵌入模型。空字符串表示进程没配模型名。
        self._legacy_embedding_model = embedding_class_name(legacy_embedding_model)
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
            await self._install_vector_extension(conn)
            # 连接池这条路径也会建索引。旧库租户列还叫 site_id 时，必须先改名，否则索引语句找不到 agent_id。
            await self._rename_legacy_agent_column(conn)
            await self._create_chunks(conn, self._dimensions)

    @staticmethod
    async def _install_vector_extension(conn) -> None:
        try:
            await conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        except Exception:  # noqa: BLE001 — 无扩展安装权限时由运维预建
            pass

    async def _create_chunks(self, conn, dimensions: int, table: str = "chunks") -> None:
        """建这一维度的类表和索引。已有表只补 embedding_model 列，不删其它类。"""
        dimensions = _positive_dimensions(dimensions)
        qualified = qualified_chunks_table(table)
        await conn.execute("CREATE SCHEMA IF NOT EXISTS agent_retrieval")
        await conn.execute(create_class_table_sql(table, dimensions))
        await self._ensure_embedding_model_column(conn, table)
        source_index, vector_index = _class_index_names(table)
        await conn.execute(
            f"CREATE INDEX IF NOT EXISTS {source_index} "
            f"ON {qualified} (agent_id, source_id)"
        )
        if hnsw_index_allowed(dimensions):
            await conn.execute(
                f"CREATE INDEX IF NOT EXISTS {vector_index} "
                f"ON {qualified} USING hnsw (embedding vector_cosine_ops) "
                "WITH (m = 16, ef_construction = 64)"
            )
        else:
            logger.warning(
                "Skipping HNSW index: embedding dimensions %s exceed %s; "
                "vector search will scan",
                dimensions,
                HNSW_MAX_DIMENSIONS,
            )

    async def _ensure_embedding_model_column(self, conn, table: str) -> None:
        """旧表没有模型名时补列。chunks 上的空值记为当前进程嵌入模型。"""
        qualified = qualified_chunks_table(table)
        await conn.execute(
            f"ALTER TABLE {qualified} ADD COLUMN IF NOT EXISTS embedding_model TEXT"
        )
        if table == "chunks" and self._legacy_embedding_model:
            await conn.execute(
                f"UPDATE {qualified} SET embedding_model = $1 WHERE embedding_model IS NULL",
                self._legacy_embedding_model,
            )
        await conn.execute(
            f"UPDATE {qualified} SET embedding_model = '' WHERE embedding_model IS NULL"
        )
        await conn.execute(
            f"ALTER TABLE {qualified} ALTER COLUMN embedding_model SET NOT NULL"
        )

    async def _table_exists(self, conn, table: str) -> bool:
        qualified_chunks_table(table)
        found = await conn.fetchval(
            "SELECT 1 FROM pg_catalog.pg_class c "
            "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            "WHERE n.nspname = 'agent_retrieval' AND c.relname = $1 AND c.relkind = 'r'",
            table,
        )
        return found is not None

    async def _list_class_tables(self, conn) -> list[str]:
        rows = await conn.fetch(
            "SELECT c.relname FROM pg_catalog.pg_class c "
            "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            "WHERE n.nspname = 'agent_retrieval' AND c.relkind = 'r' "
            "AND (c.relname = 'chunks' OR c.relname LIKE 'chunks\\_d%' ESCAPE '\\')"
        )
        names = [
            row["relname"]
            for row in rows
            if isinstance(row["relname"], str) and _CHUNKS_TABLE_NAME.fullmatch(row["relname"])
        ]
        names.sort()
        return names

    async def _connect(self):
        import asyncpg

        return await asyncpg.connect(self._dsn)

    async def current_dimensions(self) -> int | None:
        """读 chunks.embedding 的实际维度。表或列不存在时不建表，回 None。"""
        conn = await self._connect()
        try:
            typmod = await conn.fetchval(_EMBEDDING_ATTTYPMOD_SQL)
        finally:
            await conn.close()
        if isinstance(typmod, bool) or not isinstance(typmod, int) or typmod < 1:
            return None
        return typmod

    @staticmethod
    async def _rename_legacy_agent_column(conn) -> None:
        """存量库列名迁移：chunks 表的租户列旧名 site_id → agent_id。
        幂等：仅当旧列存在且新列不存在时才改名；新库无此列直接跳过。"""
        legacy = await conn.fetchval(
            "SELECT 1 FROM information_schema.columns "
            "WHERE table_schema = 'agent_retrieval' AND table_name = 'chunks' "
            "AND column_name = 'site_id'"
        )
        if legacy is None:
            return
        current = await conn.fetchval(
            "SELECT 1 FROM information_schema.columns "
            "WHERE table_schema = 'agent_retrieval' AND table_name = 'chunks' "
            "AND column_name = 'agent_id'"
        )
        if current is not None:
            return
        await conn.execute(
            "ALTER TABLE agent_retrieval.chunks RENAME COLUMN site_id TO agent_id"
        )
        await conn.execute(
            "ALTER INDEX IF EXISTS agent_retrieval.idx_agent_retrieval_chunks_site_source "
            "RENAME TO idx_agent_retrieval_chunks_agent_source"
        )

    async def ensure_schema(self, dimensions: int) -> None:
        """表不存在时按给定维度建表与索引。成功后更新 self._dimensions。"""
        dimensions = _positive_dimensions(dimensions)
        conn = await self._connect()
        try:
            await self._install_vector_extension(conn)
            await self._rename_legacy_agent_column(conn)
            await self._create_chunks(conn, dimensions)
            self._dimensions = dimensions
        finally:
            await conn.close()

    async def recreate_schema(self, new_dimensions: int) -> None:
        """单个事务内删除 chunks 并按新维度重建表与全部索引。成功后更新 self._dimensions。"""
        new_dimensions = _positive_dimensions(new_dimensions)
        conn = await self._connect()
        try:
            await self._install_vector_extension(conn)
            async with conn.transaction():
                # 只重建遗留 chunks。chunks_dN 上的其它类留在原表。
                await conn.execute(LEGACY_CHUNKS_DROP_SQL)
                await self._create_chunks(conn, new_dimensions)
            self._dimensions = new_dimensions
        finally:
            await conn.close()

    def _vector_literal(self, embedding: list[float], *, dimensions: int | None = None) -> str:
        expected = self._dimensions if dimensions is None else dimensions
        if len(embedding) != expected:
            raise ValueError(
                f"Embedding dimension {len(embedding)} does not match configured {expected}."
            )
        return "[" + ",".join(repr(value) for value in _finite_floats(embedding)) + "]"

    async def upsert_document(
        self,
        *,
        agent_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
        embedding_model: str = "",
    ) -> int:
        if not embeddings:
            return 0
        dimensions = len(embeddings[0])
        model = embedding_class_name(embedding_model)
        table = dimension_table_name(dimensions, legacy_dimensions=self._dimensions)
        qualified = qualified_chunks_table(table)
        # 只有写进遗留表、且模型名就是进程模型时，才把未标注的存量行一起换掉。
        include_nulls = table == "chunks" and model == self._legacy_embedding_model
        pool = await self._get_pool()
        metadata = json.dumps({"title": title})
        async with pool.acquire() as conn, conn.transaction():
            # 同一文件的删除与插入都在这个事务里。锁键是 agent_id:source_id 的 64-bit hash，
            # 只串行化这一份文件；事务提交或回滚时 pg_advisory_xact_lock 自动释放，其它 source 不互相阻塞。
            # 本机单测没有 pgvector，不会执行到这里。
            await conn.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))",
                f"{agent_id}:{source_id}",
            )
            await self._create_chunks(conn, dimensions, table)
            await conn.execute(
                class_row_delete_sql(table, include_legacy_nulls=include_nulls),
                agent_id,
                source_id,
                model,
            )
            for index, (content, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
                await conn.execute(
                    f"INSERT INTO {qualified} "
                    "(agent_id, source_id, chunk_index, content, embedding, embedding_model, metadata) "
                    "VALUES ($1, $2, $3, $4, $5::vector, $6, $7::jsonb)",
                    agent_id,
                    source_id,
                    index,
                    content,
                    self._vector_literal(embedding, dimensions=dimensions),
                    model,
                    metadata,
                )
        return len(chunks)

    async def delete_document(self, *, agent_id: str, source_id: str) -> None:
        """删文档时清掉每一类里的这份。新增一类不会走到这里。"""
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            tables = await self._list_class_tables(conn)
            for table in tables:
                qualified = qualified_chunks_table(table)
                await conn.execute(
                    f"DELETE FROM {qualified} WHERE agent_id = $1 AND source_id = $2",
                    agent_id,
                    source_id,
                )

    async def search(
        self,
        *,
        agent_id: str,
        embedding: list[float],
        top_k: int,
        embedding_model: str = "",
    ) -> list[RetrievedChunk]:
        dimensions = len(embedding)
        model = embedding_class_name(embedding_model)
        table = dimension_table_name(dimensions, legacy_dimensions=self._dimensions)
        include_nulls = table == "chunks" and model == self._legacy_embedding_model
        pool = await self._get_pool()
        # hnsw.ef_search 是事务局部参数。asyncpg 必须把 set_config 和查询放进同一个 transaction。
        async with pool.acquire() as conn, conn.transaction():
            if not await self._table_exists(conn, table):
                return []
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
                vector_search_sql(table, include_legacy_nulls=include_nulls),
                agent_id,
                self._vector_literal(embedding, dimensions=dimensions),
                model,
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
        self,
        *,
        agent_id: str,
        tokens: list[str],
        limit: int,
        embedding_models: list[str] | None = None,
    ) -> list[RetrievedChunk]:
        """按调用方 agent_id 做字面量子串匹配。只查这次检索登记的嵌入类。

        分数先用命中 token 数，好让多张类表能按同一把尺子合并；服务层再改成覆盖率。
        """
        if not tokens or limit < 1:
            return []
        models = (
            None
            if embedding_models is None
            else [embedding_class_name(model) for model in embedding_models]
        )
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            tables = await self._list_class_tables(conn)
            if not tables:
                tables = ["chunks"]
            groups: list[list[RetrievedChunk]] = []
            for table in tables:
                include_nulls = bool(
                    models is not None
                    and table == "chunks"
                    and self._legacy_embedding_model in models
                )
                sql, arguments = lexical_search_statement(
                    agent_id,
                    tokens,
                    limit,
                    table,
                    embedding_models=models,
                    include_legacy_nulls=include_nulls,
                )
                rows = await conn.fetch(sql, *arguments)
                groups.append(
                    [
                        RetrievedChunk(
                            source_id=row["source_id"],
                            chunk_index=row["chunk_index"],
                            content=row["content"],
                            score=float(row["score"]),
                        )
                        for row in rows
                    ]
                )
        return merge_lexical_candidates(groups, limit=limit)

    async def close(self) -> None:
        async with self._pool_lock:
            if self._pool is not None:
                await self._pool.close()
                self._pool = None
