"""向量存储：agent_retrieval schema（pgvector）。

所有查询强制携带 base_id 过滤——分区键是知识空间（Issue #209 UoW-2 起由
agent_id 换为 base_id），空间隔离是硬约束，不允许全库检索。文档只属一个
空间、只写一份；agent 经订阅使用哪些空间由 Java 侧展开后下发。
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from dataclasses import dataclass, replace
from typing import Protocol, Sequence

from agent_services.retrieval.embeddings import _finite_floats, embedding_class_name

logger = logging.getLogger(__name__)

# pgvector 的 vector 型 HNSW 索引上限。超过则不建索引，搜索仍走顺序扫描。
HNSW_MAX_DIMENSIONS = 2000


@dataclass(frozen=True)
class ChunkLocation:
    """可核对位置。页码与表格行区间不会同时出现。"""

    page: int | None = None
    sheet: str | None = None
    row_start: int | None = None
    row_end: int | None = None

    def to_meta(self) -> dict[str, object]:
        payload: dict[str, object] = {}
        if isinstance(self.page, int) and not isinstance(self.page, bool) and self.page >= 1:
            payload["page"] = self.page
        if isinstance(self.sheet, str) and self.sheet.strip():
            payload["sheet"] = self.sheet.strip()
        if isinstance(self.row_start, int) and not isinstance(self.row_start, bool) and self.row_start >= 1:
            payload["rowStart"] = self.row_start
        if isinstance(self.row_end, int) and not isinstance(self.row_end, bool) and self.row_end >= 1:
            payload["rowEnd"] = self.row_end
        return payload

    def to_api(self) -> dict[str, object]:
        return self.to_meta()


@dataclass(frozen=True)
class RetrievedChunk:
    source_id: str
    chunk_index: int
    content: str
    score: float
    title: str | None = None
    location: ChunkLocation | None = None
    # collector 累积顺序（1 起）。分数截断不得改写这个编号。
    cite: int | None = None
    # 命中所属知识空间（分区键）。store 查询时盖上；手工构造的片段缺省为空。
    base_id: str = ""

    def with_score(self, score: float) -> RetrievedChunk:
        return replace(self, score=score)

    def with_cite(self, cite: int) -> RetrievedChunk:
        return replace(self, cite=cite)

    def with_base_id(self, base_id: str) -> RetrievedChunk:
        return replace(self, base_id=base_id)


CITATION_LIMIT = 5


def location_from_meta(meta: object) -> ChunkLocation | None:
    if not isinstance(meta, dict):
        return None
    location = ChunkLocation(
        page=_positive_int(meta.get("page")),
        sheet=meta.get("sheet").strip()
        if isinstance(meta.get("sheet"), str) and meta.get("sheet").strip()
        else None,
        row_start=_positive_int(meta.get("rowStart")),
        row_end=_positive_int(meta.get("rowEnd")),
    )
    return location if location.to_meta() else None


def _positive_int(value: object) -> int | None:
    if isinstance(value, bool) or not isinstance(value, int) or value < 1:
        return None
    return value


def _meta_dict(metadata: object) -> dict[str, object]:
    if isinstance(metadata, str):
        try:
            parsed = json.loads(metadata)
        except json.JSONDecodeError:
            return {}
        return parsed if isinstance(parsed, dict) else {}
    if isinstance(metadata, dict):
        return metadata
    return {}


def _chunk_metadata(title: str, location: ChunkLocation | None) -> str:
    payload: dict[str, object] = {}
    if title:
        payload["title"] = title
    if location is not None:
        payload.update(location.to_meta())
    return json.dumps(payload)


def _chunk_from_record(row: object) -> RetrievedChunk:
    getter = row.__getitem__  # type: ignore[attr-defined]
    return chunk_from_row(
        source_id=getter("source_id"),
        chunk_index=getter("chunk_index"),
        content=getter("content"),
        score=getter("score"),
        metadata=getter("metadata"),
    )


def chunk_from_row(
    *,
    source_id: object,
    chunk_index: object,
    content: object,
    score: object,
    metadata: object = None,
) -> RetrievedChunk:
    """查询行还原片段。metadata 缺省时标题与位置为空，旧行仍可检索。"""
    meta = _meta_dict(metadata)
    raw_title = meta.get("title")
    title = raw_title.strip() if isinstance(raw_title, str) and raw_title.strip() else None
    return RetrievedChunk(
        source_id=str(source_id),
        chunk_index=int(chunk_index),
        content=str(content),
        score=float(score),
        title=title,
        location=location_from_meta(meta),
    )


def citation_sources(
    chunks: list[RetrievedChunk], *, limit: int = CITATION_LIMIT
) -> list[dict[str, object]]:
    """同一 (source_id, chunk_index) 只留最高分，顺序保持首次出现。

    超过 limit 时按分数丢掉低分项，留下的编号与相对顺序不变。
    cite 已由 collector 分配时沿用；否则用去重后的序号。
    """
    ordered: list[RetrievedChunk] = []
    position: dict[tuple[str, int], int] = {}
    for chunk in chunks:
        key = (chunk.source_id, chunk.chunk_index)
        slot = position.get(key)
        if slot is None:
            position[key] = len(ordered)
            ordered.append(chunk)
            continue
        previous = ordered[slot]
        if chunk.score > previous.score:
            ordered[slot] = previous.with_score(chunk.score)
    numbered = [
        (item.cite if item.cite is not None else index, item)
        for index, item in enumerate(ordered, start=1)
    ]
    if limit >= 0 and len(numbered) > limit:
        keep = {
            cite
            for cite, _item in sorted(numbered, key=lambda pair: (-pair[1].score, pair[0]))[:limit]
        }
        numbered = [(cite, item) for cite, item in numbered if cite in keep]
    citations: list[dict[str, object]] = []
    for cite, item in numbered:
        entry: dict[str, object] = {
            "cite": cite,
            "sourceId": item.source_id,
            "chunkIndex": item.chunk_index,
            "score": item.score,
        }
        if item.base_id:
            entry["baseId"] = item.base_id
        if item.title:
            entry["title"] = item.title
        if item.location is not None and item.location.to_api():
            entry["location"] = item.location.to_api()
        citations.append(entry)
    return citations


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


SHEET_ROWS_TABLE = "agent_retrieval.sheet_rows"


def create_sheet_rows_table_sql() -> str:
    """结构化行与 chunks 一样由 store 在首次使用时建表，不走 Flyway。"""
    return (
        f"CREATE TABLE IF NOT EXISTS {SHEET_ROWS_TABLE} ("
        "base_id TEXT NOT NULL, "
        "source_id TEXT NOT NULL, "
        "title TEXT NOT NULL, "
        "sheet TEXT NOT NULL, "
        "header_row INTEGER NOT NULL, "
        "header JSONB NOT NULL, "
        "row_number INTEGER NOT NULL, "
        "cells JSONB NOT NULL, "
        "truncated BOOLEAN NOT NULL DEFAULT FALSE, "
        "PRIMARY KEY (base_id, source_id, sheet, row_number))"
    )


def _json_text_list(value: object) -> list[str]:
    parsed = json.loads(value) if isinstance(value, str) else value
    if not isinstance(parsed, list):
        return []
    return ["" if item is None else str(item) for item in parsed]


def create_class_table_sql(table: str, dimensions: int) -> str:
    """新建一类的表。只有 CREATE IF NOT EXISTS，不删除已有类。"""
    dimensions = _positive_dimensions(dimensions)
    qualified = qualified_chunks_table(table)
    return (
        f"CREATE TABLE IF NOT EXISTS {qualified} ("
        "id BIGSERIAL PRIMARY KEY, "
        "base_id TEXT NOT NULL, "
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
        f"DELETE FROM {qualified} WHERE base_id = $1 AND source_id = $2 AND {predicate}"
    )


def prune_delete_sql(table: str) -> str:
    """差集清理（Issue #202 UoW-2）：删本档在该表中、模型名不在保留集内的行。

    只由「完整类集」写入触发。embedding_model 为 NULL 的存量行比较结果为
    NULL、不会被删（归属未知，照 include_legacy_nulls 的保守口径保留）。
    """
    qualified = qualified_chunks_table(table)
    return (
        f"DELETE FROM {qualified} WHERE base_id = $1 AND source_id = $2 "
        "AND NOT (embedding_model = ANY($3::text[]))"
    )


def _class_index_names(table: str) -> tuple[str, str]:
    """遗留表沿用原来的索引名。新维度表用自己的名字，避免和 chunks 冲突。"""
    qualified_chunks_table(table)
    if table == "chunks":
        return (
            "idx_agent_retrieval_chunks_base_source",
            "idx_agent_retrieval_chunks_embedding_hnsw",
        )
    return (
        f"idx_agent_retrieval_{table}_base_source",
        f"idx_agent_retrieval_{table}_embedding_hnsw",
    )


def _legacy_agent_index_names(table: str) -> tuple[str, str]:
    """换键前（agent_id 时代）的分区索引名，迁移改名时用。新库不会出现。"""
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
        "SELECT source_id, chunk_index, content, metadata, "
        "1 - (embedding <=> $2::vector) AS score "
        f"FROM {qualified} "
        f"WHERE base_id = $1 AND {predicate} "
        "ORDER BY embedding <=> $2::vector LIMIT $4"
    )


def like_literal_pattern(token: str) -> str:
    """收成 ILIKE 字面量模式。%、_、\\ 按字面匹配，不当成通配符。"""
    escaped = token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def lexical_search_statement(
    base_id: str,
    tokens: list[str],
    limit: int,
    table: str = "chunks",
    embedding_models: list[str] | None = None,
    include_legacy_nulls: bool = False,
) -> tuple[str, list[object]]:
    """词法 SQL 只按调用方 base_id（空间）过滤，token 和模型名只进绑定参数。

    LIMIT 之前按命中 token 数降序，同分再按 source_id、chunk_index。
    否则更宽的短 token 会占满候选窗口，把型号整段命中挤掉。
    embedding_models 给出时只查这些类；未给出时保持旧的整表字面查询。
    """
    arguments: list[object] = [base_id]
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
        "SELECT source_id, chunk_index, content, metadata, "
        f"({rank_expr})::float8 AS score "
        f"FROM {qualified_chunks_table(table)} "
        f"WHERE base_id = $1 AND ({' OR '.join(clauses)}){model_sql} "
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
        base_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
        embedding_model: str = "",
        locations: list[ChunkLocation | None] | None = None,
    ) -> int: ...

    async def delete_document(self, *, base_id: str, source_id: str) -> None: ...

    async def delete_base(self, *, base_id: str) -> None: ...

    async def list_indexed_source_ids(self, *, base_id: str) -> list[str]: ...

    async def count_chunks(self, *, base_id: str) -> int: ...

    async def prune_document_classes(
        self, *, base_id: str, source_id: str, keep_models: list[str]
    ) -> None: ...

    async def replace_sheet_rows(
        self,
        *,
        base_id: str,
        source_id: str,
        title: str,
        tables: Sequence[object],
    ) -> None: ...

    async def has_structured_tables(self, *, base_id: str) -> bool: ...

    async def load_structured_tables(self, *, base_id: str) -> list[object]: ...

    async def search(
        self,
        *,
        base_id: str,
        embedding: list[float],
        top_k: int,
        embedding_model: str = "",
    ) -> list[RetrievedChunk]: ...

    async def search_lexical(
        self,
        *,
        base_id: str,
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
            # 连接池这条路径也会建索引。旧库租户列还叫 site_id 时，必须先改名，否则索引语句找不到列。
            await self._rename_legacy_agent_column(conn)
            # 分区键换代（Issue #209 UoW-2）：存量 agent_id 列改名 base_id 后再建表/索引。
            await self._rename_partition_columns(conn)
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
        await conn.execute(create_sheet_rows_table_sql())
        await self._ensure_embedding_model_column(conn, table)
        source_index, vector_index = _class_index_names(table)
        await conn.execute(
            f"CREATE INDEX IF NOT EXISTS {source_index} "
            f"ON {qualified} (base_id, source_id)"
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
    async def _column_exists(conn, table: str, column: str) -> bool:
        found = await conn.fetchval(
            "SELECT 1 FROM information_schema.columns "
            "WHERE table_schema = 'agent_retrieval' AND table_name = $1 "
            "AND column_name = $2",
            table,
            column,
        )
        return found is not None

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

    async def _rename_partition_columns(self, conn) -> None:
        """分区键换代（Issue #209 UoW-2）：存量表 agent_id 列改名 base_id。

        幂等：仅当 agent_id 存在且 base_id 不存在时改名；新库直接跳过。
        PG 的 RENAME COLUMN 会让唯一索引/主键自动跟随新列名（索引名不变），
        故只需再把分区索引名显式改到 base 口径，与 site_id→agent_id 那次同法。
        覆盖全部类表（chunks＋chunks_dN）与 sheet_rows（其主键随列改名跟随）。
        """
        tables = await self._list_class_tables(conn)
        for table in tables:
            if not await self._column_exists(conn, table, "agent_id"):
                continue
            if await self._column_exists(conn, table, "base_id"):
                continue
            qualified = qualified_chunks_table(table)
            await conn.execute(
                f"ALTER TABLE {qualified} RENAME COLUMN agent_id TO base_id"
            )
            legacy_source, _legacy_hnsw = _legacy_agent_index_names(table)
            source_index, _hnsw_index = _class_index_names(table)
            await conn.execute(
                f"ALTER INDEX IF EXISTS agent_retrieval.{legacy_source} "
                f"RENAME TO {source_index}"
            )
        # sheet_rows 不在 chunks 表名白名单内，存在性直接查目录，不走 _table_exists。
        sheet_rows_exists = await conn.fetchval(
            "SELECT 1 FROM pg_catalog.pg_class c "
            "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            "WHERE n.nspname = 'agent_retrieval' AND c.relname = 'sheet_rows' "
            "AND c.relkind = 'r'"
        )
        if (
            sheet_rows_exists is not None
            and await self._column_exists(conn, "sheet_rows", "agent_id")
            and not await self._column_exists(conn, "sheet_rows", "base_id")
        ):
            await conn.execute(
                f"ALTER TABLE {SHEET_ROWS_TABLE} RENAME COLUMN agent_id TO base_id"
            )

    async def ensure_schema(self, dimensions: int) -> None:
        """表不存在时按给定维度建表与索引。成功后更新 self._dimensions。"""
        dimensions = _positive_dimensions(dimensions)
        conn = await self._connect()
        try:
            await self._install_vector_extension(conn)
            await self._rename_legacy_agent_column(conn)
            await self._rename_partition_columns(conn)
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
        base_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
        embedding_model: str = "",
        locations: list[ChunkLocation | None] | None = None,
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
        async with pool.acquire() as conn, conn.transaction():
            # 同一文件的删除与插入都在这个事务里。锁键是 base_id:source_id 的 64-bit hash，
            # 只串行化这一份文件；事务提交或回滚时 pg_advisory_xact_lock 自动释放，其它 source 不互相阻塞。
            # 本机单测没有 pgvector，不会执行到这里。
            await conn.execute(
                "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))",
                f"{base_id}:{source_id}",
            )
            await self._create_chunks(conn, dimensions, table)
            await conn.execute(
                class_row_delete_sql(table, include_legacy_nulls=include_nulls),
                base_id,
                source_id,
                model,
            )
            for index, (content, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
                location = None if locations is None or index >= len(locations) else locations[index]
                await conn.execute(
                    f"INSERT INTO {qualified} "
                    "(base_id, source_id, chunk_index, content, embedding, embedding_model, metadata) "
                    "VALUES ($1, $2, $3, $4, $5::vector, $6, $7::jsonb)",
                    base_id,
                    source_id,
                    index,
                    content,
                    self._vector_literal(embedding, dimensions=dimensions),
                    model,
                    _chunk_metadata(title, location),
                )
        return len(chunks)

    async def replace_sheet_rows(
        self,
        *,
        base_id: str,
        source_id: str,
        title: str,
        tables: Sequence[object],
    ) -> None:
        """按 source 整表替换结构化行。空列表表示这份文档不再带结构化数据。"""
        pool = await self._get_pool()
        async with pool.acquire() as conn, conn.transaction():
            await conn.execute("CREATE SCHEMA IF NOT EXISTS agent_retrieval")
            await conn.execute(create_sheet_rows_table_sql())
            await conn.execute(
                f"DELETE FROM {SHEET_ROWS_TABLE} WHERE base_id = $1 AND source_id = $2",
                base_id,
                source_id,
            )
            for table in tables:
                await self._insert_sheet(conn, base_id, source_id, title, table)

    @staticmethod
    async def _insert_sheet(conn, base_id: str, source_id: str, title: str, table: object) -> None:
        sheet = str(getattr(table, "sheet", "") or "")
        header_row = int(getattr(table, "header_row", 0) or 0)
        headers = ["" if cell is None else str(cell) for cell in (getattr(table, "headers", None) or [])]
        truncated = bool(getattr(table, "truncated", False))
        data_rows = list(getattr(table, "rows", None) or [])
        if not data_rows:
            await conn.execute(
                f"INSERT INTO {SHEET_ROWS_TABLE} "
                "(base_id, source_id, title, sheet, header_row, header, row_number, cells, truncated) "
                "VALUES ($1, $2, $3, $4, $5, $6::jsonb, 0, '[]'::jsonb, $7)",
                base_id,
                source_id,
                title,
                sheet,
                header_row,
                json.dumps(headers, ensure_ascii=False),
                truncated,
            )
            return
        for row in data_rows:
            cells = ["" if cell is None else str(cell) for cell in (getattr(row, "cells", None) or [])]
            await conn.execute(
                f"INSERT INTO {SHEET_ROWS_TABLE} "
                "(base_id, source_id, title, sheet, header_row, header, row_number, cells, truncated) "
                "VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8::jsonb, $9)",
                base_id,
                source_id,
                title,
                sheet,
                header_row,
                json.dumps(headers, ensure_ascii=False),
                int(getattr(row, "row_number", 0) or 0),
                json.dumps(cells, ensure_ascii=False),
                truncated,
            )

    async def has_structured_tables(self, *, base_id: str) -> bool:
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            if await conn.fetchval("SELECT to_regclass($1)", SHEET_ROWS_TABLE) is None:
                return False
            found = await conn.fetchval(
                f"SELECT 1 FROM {SHEET_ROWS_TABLE} WHERE base_id = $1 LIMIT 1",
                base_id,
            )
        return found is not None

    async def load_structured_tables(self, *, base_id: str) -> list[object]:
        from agent_services.retrieval.tables import StoredSheet

        pool = await self._get_pool()
        async with pool.acquire() as conn:
            if await conn.fetchval("SELECT to_regclass($1)", SHEET_ROWS_TABLE) is None:
                return []
            records = await conn.fetch(
                "SELECT source_id, title, sheet, header_row, header, row_number, cells, truncated "
                f"FROM {SHEET_ROWS_TABLE} WHERE base_id = $1 "
                "ORDER BY source_id, sheet, row_number",
                base_id,
            )
        grouped: dict[tuple[str, str], dict[str, object]] = {}
        order: list[tuple[str, str]] = []
        for record in records:
            key = (str(record["source_id"]), str(record["sheet"]))
            bucket = grouped.get(key)
            if bucket is None:
                bucket = {
                    "title": str(record["title"]),
                    "header_row": int(record["header_row"]),
                    "headers": _json_text_list(record["header"]),
                    "rows": [],
                    "truncated": bool(record["truncated"]),
                }
                grouped[key] = bucket
                order.append(key)
            row_number = int(record["row_number"])
            if row_number >= 1:
                rows = bucket["rows"]
                assert isinstance(rows, list)
                rows.append((row_number, _json_text_list(record["cells"])))
        loaded: list[object] = []
        for key in order:
            bucket = grouped[key]
            rows = bucket["rows"]
            assert isinstance(rows, list)
            loaded.append(
                StoredSheet(
                    source_id=key[0],
                    title=str(bucket["title"]),
                    sheet=key[1],
                    header_row=int(bucket["header_row"]),
                    headers=list(bucket["headers"]),  # type: ignore[arg-type]
                    rows=rows,
                    truncated=bool(bucket["truncated"]),
                )
            )
        return loaded

    async def delete_document(self, *, base_id: str, source_id: str) -> None:
        """删文档时清掉每一类里的这份。新增一类不会走到这里。"""
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            tables = await self._list_class_tables(conn)
            for table in tables:
                qualified = qualified_chunks_table(table)
                await conn.execute(
                    f"DELETE FROM {qualified} WHERE base_id = $1 AND source_id = $2",
                    base_id,
                    source_id,
                )
            if await conn.fetchval("SELECT to_regclass($1)", SHEET_ROWS_TABLE) is not None:
                await conn.execute(
                    f"DELETE FROM {SHEET_ROWS_TABLE} WHERE base_id = $1 AND source_id = $2",
                    base_id,
                    source_id,
                )

    async def delete_base(self, *, base_id: str) -> None:
        """整空间清理：删掉该空间在每一类表与结构化行里的全部行。"""
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            tables = await self._list_class_tables(conn)
            for table in tables:
                qualified = qualified_chunks_table(table)
                await conn.execute(
                    f"DELETE FROM {qualified} WHERE base_id = $1",
                    base_id,
                )
            if await conn.fetchval("SELECT to_regclass($1)", SHEET_ROWS_TABLE) is not None:
                await conn.execute(
                    f"DELETE FROM {SHEET_ROWS_TABLE} WHERE base_id = $1",
                    base_id,
                )

    async def list_indexed_source_ids(self, *, base_id: str) -> list[str]:
        """该空间已索引的 source 全集（类表与结构化行的并集），供存量对账。"""
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            found: set[str] = set()
            tables = await self._list_class_tables(conn)
            for table in tables:
                qualified = qualified_chunks_table(table)
                rows = await conn.fetch(
                    f"SELECT DISTINCT source_id FROM {qualified} WHERE base_id = $1",
                    base_id,
                )
                found.update(str(row["source_id"]) for row in rows)
            if await conn.fetchval("SELECT to_regclass($1)", SHEET_ROWS_TABLE) is not None:
                rows = await conn.fetch(
                    f"SELECT DISTINCT source_id FROM {SHEET_ROWS_TABLE} WHERE base_id = $1",
                    base_id,
                )
                found.update(str(row["source_id"]) for row in rows)
        return sorted(found)

    async def count_chunks(self, *, base_id: str) -> int:
        """该空间在全部嵌入类表中的块行总数，供存量对账（文档/块/spec 三数）。

        只数 chunks 类表：sheet_rows 是结构化行、不是块，不计入（与
        list_indexed_source_ids 把结构化行并入 source 集的口径刻意不同）。
        """
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            total = 0
            tables = await self._list_class_tables(conn)
            for table in tables:
                qualified = qualified_chunks_table(table)
                count = await conn.fetchval(
                    f"SELECT COUNT(*) FROM {qualified} WHERE base_id = $1",
                    base_id,
                )
                total += int(count or 0)
        return total

    async def prune_document_classes(
        self, *, base_id: str, source_id: str, keep_models: list[str]
    ) -> None:
        """删掉本档在所有类表中、不属于 keep_models 的行（已注销类的残留）。

        只删 chunks 类表，不动 sheet_rows（结构化行与嵌入类无关）。
        keep 名单先经 embedding_class_name 规范化，与写入时的类键同口径。
        清理键是（空间, 文档）：另一空间的同源文档不受影响。
        """
        keep = [embedding_class_name(model) for model in keep_models]
        pool = await self._get_pool()
        async with pool.acquire() as conn:
            tables = await self._list_class_tables(conn)
            for table in tables:
                await conn.execute(prune_delete_sql(table), base_id, source_id, keep)

    async def search(
        self,
        *,
        base_id: str,
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
                base_id,
                self._vector_literal(embedding, dimensions=dimensions),
                model,
                top_k,
            )
        return [_chunk_from_record(row).with_base_id(base_id) for row in rows]

    async def search_lexical(
        self,
        *,
        base_id: str,
        tokens: list[str],
        limit: int,
        embedding_models: list[str] | None = None,
    ) -> list[RetrievedChunk]:
        """按调用方 base_id（空间）做字面量子串匹配。只查这次检索登记的嵌入类。

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
                    base_id,
                    tokens,
                    limit,
                    table,
                    embedding_models=models,
                    include_legacy_nulls=include_nulls,
                )
                rows = await conn.fetch(sql, *arguments)
                groups.append([_chunk_from_record(row) for row in rows])
        return [
            chunk.with_base_id(base_id)
            for chunk in merge_lexical_candidates(groups, limit=limit)
        ]

    async def close(self) -> None:
        async with self._pool_lock:
            if self._pool is not None:
                await self._pool.close()
                self._pool = None
