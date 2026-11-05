"""检索服务：分块 → embedding → 向量存取；base_id（知识空间）全链路强制。

分区键 Issue #209 UoW-2 起由 agent_id 换为 base_id：文档只属一个空间、只写
一份；读取时调用方给出空间集，单空间走原管线，多空间各空间独立走完管线后
按名次合并（与多类合并同一先例）。
"""

import logging
import re
from dataclasses import dataclass

import anyio
from pydantic_ai import Agent
from pydantic_ai.models import Model

from agent_services.agent.model_factory import create_model, create_model_for_entry
from agent_services.config import (
    RETRIEVAL_MAX_CHUNKS_PER_SOURCE_DEFAULT,
    RETRIEVAL_MIN_SCORE_DEFAULT,
    RETRIEVAL_RERANK_CUTOFF_DEFAULT,
    RETRIEVAL_RERANK_ENABLED_DEFAULT,
    RETRIEVAL_RERANK_MAX_CANDIDATES_DEFAULT,
    RETRIEVAL_RERANK_TIMEOUT_SECONDS_DEFAULT,
    RETRIEVAL_SCORE_RATIO_FLOOR_DEFAULT,
    Settings,
)
from agent_services.retrieval.chunking import split_text
from agent_services.retrieval.embeddings import (
    MAX_EMBEDDING_CLASSES,
    EmbeddingClient,
    create_embedding_client,
    embedding_class_name,
)
from agent_services.retrieval.store import ChunkLocation, RetrievalStore, RetrievedChunk
from agent_services.schemas.chat import ModelSelection

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class IndexSegment:
    """索引入库的一段正文及其位置。不带 segments 时不使用。"""

    text: str
    page: int | None = None
    sheet: str | None = None
    row_start: int | None = None
    row_end: int | None = None


@dataclass(frozen=True)
class IndexTableRow:
    """结构化行。row_number 是工作表原始行号，不是切块序号。"""

    row_number: int
    cells: list[str]


@dataclass(frozen=True)
class IndexTable:
    """一张表。缺省（None）的 upsert 不改变切块，并清掉该 source 已有的结构化行。"""

    sheet: str
    header_row: int
    headers: list[str]
    rows: list[IndexTableRow]
    truncated: bool = False


def _chunks_for_index(
    content: str, segments: list[IndexSegment] | None
) -> tuple[list[str], list[ChunkLocation | None] | None]:
    """无 segments 时切块与现行 split_text(content) 相同，位置为 None。

    有 segments 时每段单独 split_text，位置抄到该段切出的每个块上，不跨段合并。
    """
    if segments is None:
        return split_text(content), None
    chunks: list[str] = []
    locations: list[ChunkLocation | None] = []
    for segment in segments:
        location = ChunkLocation(
            page=segment.page,
            sheet=segment.sheet,
            row_start=segment.row_start,
            row_end=segment.row_end,
        )
        if not location.to_meta():
            location = None
        for piece in split_text(segment.text):
            chunks.append(piece)
            locations.append(location)
    return chunks, locations


# 只用来量向量长度，不写入索引。
_DIMENSION_PROBE_TEXT = "dimension probe"


class DimensionChangeRequired(Exception):
    """探针维度与列维度不同，且调用方未确认重建。"""

    def __init__(self, dimensions: int, column_dimensions: int) -> None:
        super().__init__("Embedding dimensions differ from the vector column.")
        self.dimensions = dimensions
        self.column_dimensions = column_dimensions


@dataclass(frozen=True)
class ReembedPrepared:
    dimensions: int
    column_dimensions: int | None
    recreated: bool


@dataclass(frozen=True)
class EmbeddingClass:
    """一次检索或写入用的一类：模型名加上它自己的嵌入客户端。"""

    name: str
    client: EmbeddingClient | None


@dataclass(frozen=True)
class RetrievalSpace:
    """一次检索中的一个知识空间，带它自己的登记嵌入类集（Issue #209 UoW-3）。

    嵌入登记是空间级的：同一请求里每个空间用各自的类集检索，不再有一份
    请求级类集套给所有空间。embedding_classes 为 None 或空 = 该空间未单独
    登记类，检索时落请求级嵌入器/进程默认类，与未登记口径一致。
    """

    base_id: str
    embedding_classes: list[EmbeddingClass] | None = None


DEFAULT_TOP_K = 4
DEFAULT_MIN_SCORE = RETRIEVAL_MIN_SCORE_DEFAULT
DEFAULT_SCORE_RATIO_FLOOR = RETRIEVAL_SCORE_RATIO_FLOOR_DEFAULT
# 1000 字一块时，二十多万字的表格抽取会超过 256 块，索引在嵌入前就被 400 拒绝。
MAX_UPSERT_CHUNKS = 512
# 与检索 topK 上限一致：词法通道在向量窗口之外另取候选，再和向量结果一起截到 top_k。
LEXICAL_CANDIDATE_LIMIT = 20
MAX_CODE_TOKENS = 32
MAX_QUERY_TERMS = 24
RERANK_SNIPPET_CHARS = 300

# 连字符/点号型号整段保留，例如 XJ-900、v1.2。
_MODEL_TOKEN = re.compile(r"[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)+")
_ALNUM_TOKEN = re.compile(r"[A-Za-z0-9]+")
# 只匹配原文里已经是小写的英数词，避免把型号里的大写片段再拆一遍。
_LOWER_ALNUM = re.compile(r"[a-z0-9]+")
# 汉字、平假名、片假名。相邻的这三类算同一连续段。
_CJK_SEGMENT = re.compile("[\u4e00-\u9fff\u3040-\u309f\u30a0-\u30ff]+")
_JSON_INT_ARRAY = re.compile(r"\[\s*(?:\d+\s*(?:,\s*\d+\s*)*)?\]")

_RERANK_INSTRUCTIONS = (
    "You reorder search snippets by relevance to the question. "
    "Reply with only a JSON array of candidate numbers, most relevant first. "
    "Do not add any other text."
)

_RERANK_CUTOFF_INSTRUCTIONS = (
    "You select search snippets that are relevant to the question. "
    "Reply with only a JSON array of the candidate numbers that are relevant, "
    "most relevant first. Omit candidates that do not help answer the question. "
    "Do not add any other text."
)


class RetrievalChunkLimitExceeded(Exception):
    """单文档分块数超过护栏。这是请求问题，由 API 映射为 400，不是外部依赖故障。"""


def apply_min_score(chunks: list[RetrievedChunk], min_score: float) -> list[RetrievedChunk]:
    """丢掉低于门槛的向量命中。

    store 已按距离返回最近的 top_k，更高分不会落在这个窗口外面，因此过滤后不再补位。
    min_score <= 0 表示关闭门槛，原样返回。
    """
    if min_score <= 0:
        return list(chunks)
    return [chunk for chunk in chunks if chunk.score >= min_score]


def apply_score_ratio_floor(
    chunks: list[RetrievedChunk], ratio_floor: float
) -> list[RetrievedChunk]:
    """融合后相对裁剪：丢掉分数低于「本批最高分 × ratio_floor」的命中。

    绝对门槛（apply_min_score）挡不住「最高分 0.60、尾巴 0.10」这种同批内的弱命中——
    它们照样进 topK 并成为引用（M11 eval citation_precision 0.75 的成因）。相对裁剪按
    本批最高分定标，不依赖嵌入模型的绝对分数分布；合法的多文档命中分数通常接近最高分，
    不受影响。ratio_floor <= 0 或最高分 <= 0 时原样返回；最高分本身永远保留。
    """
    if ratio_floor <= 0 or len(chunks) < 2:
        return list(chunks)
    top = max(chunk.score for chunk in chunks)
    if top <= 0:
        return list(chunks)
    cutoff = top * ratio_floor
    return [chunk for chunk in chunks if chunk.score >= cutoff]


def apply_per_source_cap(
    chunks: list[RetrievedChunk], max_per_source: int
) -> list[RetrievedChunk]:
    """同源多样性上限：按融合序保留每 source_id 的前 max_per_source 块，其余丢弃。

    长文档的相邻块分数接近、会整排占满 topK，让其他文档的相关块进不来
    （真嵌入探针：保养单题 top4 全是同一指南的块）。裁剪在 rerank 之前，
    让重排窗口本身就是多样化的；max_per_source <= 0 时原样返回。
    """
    if max_per_source <= 0:
        return list(chunks)
    kept: list[RetrievedChunk] = []
    counts: dict[str, int] = {}
    for chunk in chunks:
        seen = counts.get(chunk.source_id, 0)
        if seen >= max_per_source:
            continue
        counts[chunk.source_id] = seen + 1
        kept.append(chunk)
    return kept


def code_tokens(query: str) -> list[str]:
    """抽出代码型 token。型号整段保留，并另收含数字且长度≥2、或纯字母数字长度≥4 的片段。"""
    found: list[str] = []
    seen: set[str] = set()

    def add(token: str) -> None:
        key = token.casefold()
        if key in seen:
            return
        seen.add(key)
        found.append(token)

    for match in _MODEL_TOKEN.finditer(query):
        add(match.group(0))
    for match in _ALNUM_TOKEN.finditer(query):
        token = match.group(0)
        has_digit = any(char.isdigit() for char in token)
        if (has_digit and len(token) >= 2) or (not has_digit and len(token) >= 4):
            add(token)
    return found[:MAX_CODE_TOKENS]


def query_terms(query: str) -> list[str]:
    """代码型 token 优先，其次小写英数词，最后是 CJK 单字或重叠 bigram。总数不超过 24。"""
    terms = code_tokens(query)[:MAX_QUERY_TERMS]
    seen = {token.casefold() for token in terms}

    def add(token: str) -> None:
        if len(terms) >= MAX_QUERY_TERMS:
            return
        key = token.casefold()
        if key in seen:
            return
        seen.add(key)
        terms.append(token)

    for match in _LOWER_ALNUM.finditer(query):
        token = match.group(0)
        if len(token) < 2:
            continue
        add(token)
        if len(terms) >= MAX_QUERY_TERMS:
            return terms
    for match in _CJK_SEGMENT.finditer(query):
        segment = match.group(0)
        if len(segment) == 1:
            pieces = [segment]
        else:
            pieces = [segment[index : index + 2] for index in range(len(segment) - 1)]
        for piece in pieces:
            add(piece)
            if len(terms) >= MAX_QUERY_TERMS:
                return terms
    return terms


def code_coverage(content: str, tokens: list[str]) -> float:
    """命中的 term 数 / term 总数。子串命中即可；比较时忽略 ASCII 大小写。"""
    if not tokens:
        return 0.0
    folded = content.casefold()
    matched = sum(1 for token in tokens if token.casefold() in folded)
    return matched / len(tokens)


def _ranked_hits(
    vector_hits: list[RetrievedChunk],
    lexical_hits: list[RetrievedChunk],
    tokens: list[str],
) -> list[RetrievedChunk]:
    """向量命中与词法命中按 (source_id, chunk_index) 合并，不截断。

    只在向量侧的分数保持余弦。词法命中的分数是 term 覆盖率。两边都有时取较大值。
    覆盖率为 0 的词法行丢弃。
    """
    chosen: dict[tuple[str, int], RetrievedChunk] = {
        (hit.source_id, hit.chunk_index): hit for hit in vector_hits
    }
    for hit in lexical_hits:
        coverage = code_coverage(hit.content, tokens)
        if coverage <= 0:
            continue
        key = (hit.source_id, hit.chunk_index)
        previous = chosen.get(key)
        if previous is None:
            chosen[key] = hit.with_score(coverage)
            continue
        chosen[key] = previous.with_score(max(previous.score, coverage))
    return sorted(chosen.values(), key=lambda item: (-item.score, item.source_id, item.chunk_index))


def merge_by_rank(paths: list[list[RetrievedChunk]]) -> list[RetrievedChunk]:
    """类之间按名次相加。分数是各路 1/rank 之和，不读取各路原来的余弦。

    同一 (source_id, chunk_index) 出现在多路时名次相加。正文留名次最好的那一路。
    并列时按 source_id、chunk_index，保证只有一路时顺序稳定。
    """
    totals: dict[tuple[str, int], float] = {}
    best_rank: dict[tuple[str, int], int] = {}
    chosen: dict[tuple[str, int], RetrievedChunk] = {}
    for path in paths:
        for rank, chunk in enumerate(path, start=1):
            key = (chunk.source_id, chunk.chunk_index)
            totals[key] = totals.get(key, 0.0) + (1.0 / rank)
            previous = best_rank.get(key)
            if previous is None or rank < previous:
                best_rank[key] = rank
                base = chunk
            else:
                base = chosen[key]
            chosen[key] = base.with_score(totals[key])
    return sorted(chosen.values(), key=lambda item: (-item.score, item.source_id, item.chunk_index))


def lexical_rank_path(
    lexical_hits: list[RetrievedChunk], tokens: list[str]
) -> list[RetrievedChunk]:
    """字面命中单独成一路名次。覆盖率只用来给这一路排序。"""
    ranked: list[RetrievedChunk] = []
    for hit in lexical_hits:
        coverage = code_coverage(hit.content, tokens)
        if coverage <= 0:
            continue
        ranked.append(hit.with_score(coverage))
    ranked.sort(key=lambda item: (-item.score, item.source_id, item.chunk_index))
    return ranked


def merge_code_hits(
    vector_hits: list[RetrievedChunk],
    lexical_hits: list[RetrievedChunk],
    tokens: list[str],
    *,
    top_k: int,
) -> list[RetrievedChunk]:
    """向量命中与词法命中合并后按分数降序截到 top_k。

    计分对任意 term 成立：term 在 content 里子串出现即算命中。
    """
    return _ranked_hits(vector_hits, lexical_hits, tokens)[:top_k]


def parse_rerank_indices(text: str, count: int) -> list[int] | None:
    """从模型回复抽出 1-based 候选编号，去重后把没提到的下标按原顺序补在后面。

    优先取第一个 JSON 整数数组；没有数组时再扫全文里的整数。一个合法编号都没有则无法排序。
    """
    if count < 1:
        return None
    array_match = _JSON_INT_ARRAY.search(text)
    source = array_match.group(0) if array_match else text
    chosen: list[int] = []
    seen: set[int] = set()
    for raw in re.findall(r"\d+", source):
        number = int(raw)
        if number < 1 or number > count or number in seen:
            continue
        seen.add(number)
        chosen.append(number - 1)
    if not chosen:
        return None
    chosen_set = set(chosen)
    for index in range(count):
        if index not in chosen_set:
            chosen.append(index)
    return chosen


def parse_rerank_selection(text: str, count: int) -> list[int] | None:
    """裁门模式的严格解析：只认第一个 JSON 整数数组，被省略的编号就是被丢弃的候选。

    与重排模式不同，这里不扫描散文里的数字、不补位；空数组或无数组一律返回 None，
    由调用方回退到融合原序——模型一次含糊的回答不能把结果清零。
    """
    if count < 1:
        return None
    array_match = _JSON_INT_ARRAY.search(text)
    if array_match is None:
        return None
    chosen: list[int] = []
    seen: set[int] = set()
    for raw in re.findall(r"\d+", array_match.group(0)):
        number = int(raw)
        if number < 1 or number > count or number in seen:
            continue
        seen.add(number)
        chosen.append(number - 1)
    if not chosen:
        return None
    return chosen


def _rerank_prompt(query: str, candidates: list[RetrievedChunk], *, cutoff: bool = False) -> str:
    lines = [f"Question: {query}", "Candidates:"]
    for number, chunk in enumerate(candidates, start=1):
        lines.append(f"[{number}] {chunk.content[:RERANK_SNIPPET_CHARS]}")
    if cutoff:
        lines.append("Return a JSON array of the relevant candidate numbers, most relevant first.")
    else:
        lines.append("Return a JSON array of the candidate numbers ordered by relevance.")
    return "\n".join(lines)


class RetrievalService:
    def __init__(
        self,
        embedder: EmbeddingClient | None,
        store: RetrievalStore,
        *,
        min_score: float = DEFAULT_MIN_SCORE,
        score_ratio_floor: float = DEFAULT_SCORE_RATIO_FLOOR,
        settings: Settings | None = None,
        rerank_model: Model | None = None,
        rerank_enabled: bool | None = None,
        rerank_timeout_seconds: float | None = None,
        rerank_max_candidates: int | None = None,
        rerank_cutoff: bool | None = None,
        max_chunks_per_source: int | None = None,
        embedding_model: str = "",
    ) -> None:
        self._embedder = embedder
        self._store = store
        self._embedding_model = embedding_class_name(embedding_model) if embedding_model else ""
        self._min_score = min_score
        self._score_ratio_floor = score_ratio_floor
        self._settings = settings
        self._rerank_model = rerank_model
        self._rerank_enabled = (
            rerank_enabled
            if rerank_enabled is not None
            else settings.retrieval_rerank_enabled
            if settings is not None
            else RETRIEVAL_RERANK_ENABLED_DEFAULT
        )
        self._rerank_timeout_seconds = (
            rerank_timeout_seconds
            if rerank_timeout_seconds is not None
            else settings.retrieval_rerank_timeout_seconds
            if settings is not None
            else RETRIEVAL_RERANK_TIMEOUT_SECONDS_DEFAULT
        )
        self._rerank_max_candidates = (
            rerank_max_candidates
            if rerank_max_candidates is not None
            else settings.retrieval_rerank_max_candidates
            if settings is not None
            else RETRIEVAL_RERANK_MAX_CANDIDATES_DEFAULT
        )
        self._rerank_cutoff = (
            rerank_cutoff
            if rerank_cutoff is not None
            else settings.retrieval_rerank_cutoff
            if settings is not None
            else RETRIEVAL_RERANK_CUTOFF_DEFAULT
        )
        self._max_chunks_per_source = (
            max_chunks_per_source
            if max_chunks_per_source is not None
            else settings.retrieval_max_chunks_per_source
            if settings is not None
            else RETRIEVAL_MAX_CHUNKS_PER_SOURCE_DEFAULT
        )
        self._degraded = False
        self._last_error: str | None = None
        self._rerank_applied = False
        self._retrieval_incomplete = False

    @property
    def has_process_embedder(self) -> bool:
        return self._embedder is not None

    @property
    def degraded(self) -> bool:
        return self._degraded

    @property
    def last_error(self) -> str | None:
        return self._last_error

    @property
    def rerank_applied(self) -> bool:
        return self._rerank_applied

    def _resolve_classes(
        self,
        embedder: EmbeddingClient | None,
        embedding_classes: list[EmbeddingClass] | None,
        embedding_model: str | None,
    ) -> list[EmbeddingClass]:
        if embedding_classes:
            chosen: list[EmbeddingClass] = []
            seen: set[str] = set()
            for item in embedding_classes:
                name = embedding_class_name(item.name)
                if name in seen:
                    continue
                seen.add(name)
                chosen.append(EmbeddingClass(name, item.client))
            if len(chosen) > MAX_EMBEDDING_CLASSES:
                raise ValueError(
                    f"embeddingClasses accepts at most {MAX_EMBEDDING_CLASSES} classes."
                )
            if not chosen:
                raise RuntimeError("Embedding client is not configured.")
            return chosen
        client = embedder or self._embedder
        if client is None:
            raise RuntimeError("Embedding client is not configured.")
        name = embedding_class_name(embedding_model) if embedding_model else self._embedding_model
        return [EmbeddingClass(name, client)]

    async def _upsert_one(
        self,
        *,
        base_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        item: EmbeddingClass,
        locations: list[ChunkLocation | None] | None = None,
    ) -> int:
        if item.client is None:
            raise RuntimeError("Embedding client is not configured.")
        embeddings = await item.client.embed_documents(chunks)
        stored = {
            "base_id": base_id,
            "source_id": source_id,
            "title": title,
            "chunks": chunks,
            "embeddings": embeddings,
            "embedding_model": item.name,
        }
        if locations is not None:
            stored["locations"] = locations
        return await self._store.upsert_document(**stored)

    async def upsert_document(
        self,
        *,
        base_id: str,
        source_id: str,
        title: str,
        content: str,
        embedder: EmbeddingClient | None = None,
        embedding_model: str | None = None,
        embedding_classes: list[EmbeddingClass] | None = None,
        segments: list[IndexSegment] | None = None,
        tables: list[IndexTable] | None = None,
        prune_stale_classes: bool = False,
    ) -> int:
        chunks, locations = _chunks_for_index(content, segments)
        if not chunks:
            await self._store.delete_document(base_id=base_id, source_id=source_id)
            await self._replace_sheet_rows(base_id, source_id, title, tables)
            return 0
        if len(chunks) > MAX_UPSERT_CHUNKS:
            raise RetrievalChunkLimitExceeded(
                f"Document exceeds the maximum of {MAX_UPSERT_CHUNKS} chunks."
            )
        classes = self._resolve_classes(embedder, embedding_classes, embedding_model)
        if len(classes) == 1:
            count = await self._upsert_one(
                base_id=base_id,
                source_id=source_id,
                title=title,
                chunks=chunks,
                item=classes[0],
                locations=locations,
            )
            if prune_stale_classes:
                await self._prune_stale_classes(base_id, source_id, classes)
            await self._replace_sheet_rows(base_id, source_id, title, tables)
            return count
        count = 0
        failures = 0
        for item in classes:
            try:
                count = await self._upsert_one(
                    base_id=base_id,
                    source_id=source_id,
                    title=title,
                    chunks=chunks,
                    item=item,
                    locations=locations,
                )
            except Exception:
                failures += 1
                logger.warning(
                    "retrieval upsert failed for embedding class %s",
                    item.name,
                    exc_info=True,
                )
        if failures:
            raise RuntimeError("One or more embedding classes failed to index.")
        if prune_stale_classes:
            await self._prune_stale_classes(base_id, source_id, classes)
        await self._replace_sheet_rows(base_id, source_id, title, tables)
        return count

    async def _prune_stale_classes(
        self, base_id: str, source_id: str, classes: list[EmbeddingClass]
    ) -> None:
        """完整类集写入成功后，清理该文档在未登记类下的残留行（Issue #202 UoW-2）。

        清理键是（空间, 文档）：差集只在本空间内算，另一空间的同源文档不受影响
        （Issue #209 UoW-2）。只在调用方明示本次是完整类集（prune_stale_classes）
        时执行：fill-missing 子集写绝不走到这里。store 未实现该方法（测试替身等）
        时跳过，与 _replace_sheet_rows 的 getattr 口径一致；生产 PgVector store
        恒实现。清理失败照常上抛——Java 侧会把文档标 FAILED，不假装索引完全同步。
        """
        prune = getattr(self._store, "prune_document_classes", None)
        if prune is None:
            return
        await prune(
            base_id=base_id,
            source_id=source_id,
            keep_models=[item.name for item in classes],
        )

    async def _replace_sheet_rows(
        self,
        base_id: str,
        source_id: str,
        title: str,
        tables: list[IndexTable] | None,
    ) -> None:
        """chunks 写完后整份替换一次。不带 tables 时清掉该 source 的结构化行，切块路径不变。"""
        replace = getattr(self._store, "replace_sheet_rows", None)
        if replace is None:
            return
        await replace(
            base_id=base_id,
            source_id=source_id,
            title=title,
            tables=[] if tables is None else tables,
        )

    async def has_structured_tables(self, base_id: str) -> bool:
        probe = getattr(self._store, "has_structured_tables", None)
        if probe is None:
            return False
        return bool(await probe(base_id=base_id))

    async def load_structured_tables(self, base_id: str) -> list[object]:
        load = getattr(self._store, "load_structured_tables", None)
        if load is None:
            return []
        loaded = await load(base_id=base_id)
        return list(loaded)

    async def prepare_reembed(
        self,
        *,
        embedder: EmbeddingClient | None = None,
        confirm_recreate: bool = False,
    ) -> ReembedPrepared:
        """探针单套 embedder 的维度，必要时建或重建遗留 chunks 表。

        多类索引的表由 upsert 按向量长度另建，不经过这里。确认重建只 DROP 遗留 chunks。
        """
        client = embedder or self._embedder
        if client is None:
            raise RuntimeError("Embedding client is not configured.")
        vectors = await client.embed_documents([_DIMENSION_PROBE_TEXT])
        if len(vectors) != 1 or not vectors[0]:
            raise RuntimeError("Embedding probe returned no vector.")
        new_dim = len(vectors[0])
        column_dim = await self._store.current_dimensions()
        if column_dim is None:
            await self._store.ensure_schema(new_dim)
            return ReembedPrepared(
                dimensions=new_dim, column_dimensions=None, recreated=False
            )
        if new_dim == column_dim:
            return ReembedPrepared(
                dimensions=new_dim, column_dimensions=column_dim, recreated=False
            )
        if not confirm_recreate:
            raise DimensionChangeRequired(new_dim, column_dim)
        await self._store.recreate_schema(new_dim)
        return ReembedPrepared(
            dimensions=new_dim, column_dimensions=column_dim, recreated=True
        )

    async def delete_document(self, *, base_id: str, source_id: str) -> None:
        await self._store.delete_document(base_id=base_id, source_id=source_id)

    async def delete_base_documents(self, *, base_id: str) -> None:
        """整空间清理（空间删除时）。store 未实现时按未实现跳过，与 prune 同口径。"""
        delete = getattr(self._store, "delete_base", None)
        if delete is None:
            return
        await delete(base_id=base_id)

    async def list_indexed_documents(self, *, base_id: str) -> list[str]:
        """该空间已索引的 source 全集，供 Java 对账。store 未实现时回空集。"""
        list_ids = getattr(self._store, "list_indexed_source_ids", None)
        if list_ids is None:
            return []
        return list(await list_ids(base_id=base_id))

    async def count_indexed_chunks(self, *, base_id: str) -> int:
        """该空间的块行总数（全部嵌入类表求和），供 Java 对账。store 未实现时回 0。"""
        count = getattr(self._store, "count_chunks", None)
        if count is None:
            return 0
        return int(await count(base_id=base_id))

    def _resolve_rerank_model(self, selection: ModelSelection | None) -> Model | None:
        """请求里的模型入口优先；建不起来再用测试注入或进程 AI_MODEL。"""
        if selection is not None:
            handle = create_model_for_entry(
                spec=selection.spec,
                api_key=selection.api_key,
                base_url=selection.base_url,
                settings=self._settings,
            )
            if handle.model is not None:
                return handle.model
            logger.warning("retrieval rerank model selection was not built; trying the process model")
        if self._rerank_model is not None:
            return self._rerank_model
        if self._settings is None:
            return None
        handle = create_model(self._settings)
        if handle.model is None:
            return None
        return handle.model

    async def _rerank(
        self,
        model: Model,
        query: str,
        window: list[RetrievedChunk],
        tail: list[RetrievedChunk],
    ) -> list[RetrievedChunk]:
        """裁门模式只返回模型选中的窗口候选（尾巴不回填）；重排模式维持窗口重排＋尾巴拼接。

        两种模式解析失败都抛 ValueError，由 search 回退融合原序。
        """
        if self._rerank_cutoff:
            agent = Agent(model, system_prompt=_RERANK_CUTOFF_INSTRUCTIONS)
            with anyio.fail_after(self._rerank_timeout_seconds):
                result = await agent.run(_rerank_prompt(query, window, cutoff=True))
            selection = parse_rerank_selection(str(result.output), len(window))
            if selection is None:
                raise ValueError("unusable rerank selection")
            return [window[index] for index in selection]
        agent = Agent(model, system_prompt=_RERANK_INSTRUCTIONS)
        with anyio.fail_after(self._rerank_timeout_seconds):
            result = await agent.run(_rerank_prompt(query, window))
        order = parse_rerank_indices(str(result.output), len(window))
        if order is None:
            raise ValueError("unusable rerank order")
        return [window[index] for index in order] + tail

    async def _lexical_hits(
        self, base_id: str, query: str, classes: list[EmbeddingClass]
    ) -> tuple[list[str], list[RetrievedChunk]]:
        terms = query_terms(query)
        if not terms:
            return terms, []
        hits = await self._store.search_lexical(
            base_id=base_id,
            tokens=terms,
            limit=LEXICAL_CANDIDATE_LIMIT,
            embedding_models=[item.name for item in classes],
        )
        return terms, hits

    async def _finish_with_rerank(
        self,
        fused: list[RetrievedChunk],
        *,
        query: str,
        top_k: int,
        model_selection: ModelSelection | None,
    ) -> list[RetrievedChunk]:
        ordered = fused
        # 重排模式维持旧触发（候选多于 topK 才值得重排）；裁门模式候选 ≥2 即触发，
        # 否则小知识库里融合后的 2~3 条近失命中永远等不到语义把关（对抗探针实测）。
        rerank_worthwhile = len(fused) > top_k or (self._rerank_cutoff and len(fused) >= 2)
        if self._rerank_enabled and self._rerank_max_candidates > 0 and rerank_worthwhile:
            model = self._resolve_rerank_model(model_selection)
            if model is not None:
                window = fused[: self._rerank_max_candidates]
                try:
                    ordered = await self._rerank(model, query, window, fused[len(window) :])
                    self._rerank_applied = True
                except TimeoutError:
                    logger.warning("retrieval rerank timed out; keeping fused order")
                except Exception as exc:  # noqa: BLE001 — rerank 是增强，失败不让检索失败
                    logger.warning(
                        "retrieval rerank failed (%s); keeping fused order",
                        type(exc).__name__,
                    )
        return ordered[:top_k]

    async def _lexical_fused(
        self,
        *,
        base_id: str,
        query: str,
        classes: list[EmbeddingClass],
    ) -> list[RetrievedChunk]:
        """纯字面一路（Issue #202 UoW-3）：覆盖率成一路名次、per-source 上限，不收尾。

        调用方须已置 _retrieval_incomplete；字面无命中时返回空（degraded 仍真，
        不假装正常空结果）。分数门 min_score/ratio_floor 是向量余弦口径，不套到字面覆盖率上。
        """
        terms, lexical_hits = await self._lexical_hits(base_id, query, classes)
        return apply_per_source_cap(
            lexical_rank_path(lexical_hits, terms), self._max_chunks_per_source
        )

    async def _search_one_fused(
        self,
        item: EmbeddingClass,
        *,
        base_id: str,
        query: str,
        top_k: int,
    ) -> list[RetrievedChunk]:
        """单类单空间的融合结果（不走 rerank 收尾）：余弦、字面覆盖率取较大值、相对裁剪。"""
        if item.client is None:
            raise RuntimeError("Embedding client is not configured.")
        try:
            embedding = await item.client.embed_query(query)
            vector_hits = apply_min_score(
                await self._store.search(
                    base_id=base_id,
                    embedding=embedding,
                    top_k=top_k,
                    embedding_model=item.name,
                ),
                self._min_score,
            )
        except Exception as exc:  # noqa: BLE001 — 向量通道故障降级纯字面（Issue #202 UoW-3）
            # 字面通道（ILIKE）不依赖嵌入：向量侧抖动时用它兜底并标不完整，
            # 与多类路径「一类失败仍出字面结果」对齐。字面再失败则照常上抛，
            # 由 retrieve_chunks 整次降级为空。
            logger.warning(
                "retrieval vector path failed for class %s (%s); falling back to lexical only",
                item.name,
                type(exc).__name__,
            )
            self._retrieval_incomplete = True
            return await self._lexical_fused(base_id=base_id, query=query, classes=[item])
        terms, lexical_hits = await self._lexical_hits(base_id, query, [item])
        return apply_per_source_cap(
            apply_score_ratio_floor(
                _ranked_hits(vector_hits, lexical_hits, terms), self._score_ratio_floor
            ),
            self._max_chunks_per_source,
        )

    async def _search_one(
        self,
        item: EmbeddingClass,
        *,
        base_id: str,
        query: str,
        top_k: int,
        model_selection: ModelSelection | None,
    ) -> list[RetrievedChunk]:
        """只有一类时保持原来的余弦、字面覆盖率取较大值、相对裁剪。"""
        fused = await self._search_one_fused(item, base_id=base_id, query=query, top_k=top_k)
        return await self._finish_with_rerank(
            fused, query=query, top_k=top_k, model_selection=model_selection
        )

    async def _class_vector_hits(
        self, item: EmbeddingClass, *, base_id: str, query: str, top_k: int
    ) -> list[RetrievedChunk]:
        """一类内部：余弦、绝对分门槛、相对裁剪。名次从这份结果的顺序来。"""
        if item.client is None:
            raise RuntimeError("Embedding client is not configured.")
        embedding = await item.client.embed_query(query)
        raw = await self._store.search(
            base_id=base_id,
            embedding=embedding,
            top_k=top_k,
            embedding_model=item.name,
        )
        return apply_score_ratio_floor(apply_min_score(raw, self._min_score), self._score_ratio_floor)

    async def _search_many_fused(
        self,
        classes: list[EmbeddingClass],
        *,
        base_id: str,
        query: str,
        top_k: int,
    ) -> list[RetrievedChunk]:
        """多类单空间的融合结果（不走 rerank 收尾）：各类一路＋字面一路，按名次合并。"""
        paths: list[list[RetrievedChunk]] = []
        failures = 0
        for item in classes:
            try:
                paths.append(
                    await self._class_vector_hits(item, base_id=base_id, query=query, top_k=top_k)
                )
            except Exception as exc:  # noqa: BLE001 — 一类失败跳过，其它类继续
                failures += 1
                logger.warning(
                    "retrieval embedding class %s failed (%s); skipping its vector hits",
                    item.name,
                    type(exc).__name__,
                )
        if failures == len(classes):
            # 全部类向量失败时与单类同口径（Issue #202 UoW-3）：降级纯字面，
            # 不整次落空；置 _retrieval_incomplete 让 degraded 随之成立。
            self._retrieval_incomplete = True
            return await self._lexical_fused(base_id=base_id, query=query, classes=classes)
        if failures:
            self._retrieval_incomplete = True
        terms, lexical_hits = await self._lexical_hits(base_id, query, classes)
        lexical_path = lexical_rank_path(lexical_hits, terms)
        ranked_paths = [path for path in paths if path]
        if lexical_path:
            ranked_paths.append(lexical_path)
        return apply_per_source_cap(merge_by_rank(ranked_paths), self._max_chunks_per_source)

    async def _search_many(
        self,
        classes: list[EmbeddingClass],
        *,
        base_id: str,
        query: str,
        top_k: int,
        model_selection: ModelSelection | None,
    ) -> list[RetrievedChunk]:
        fused = await self._search_many_fused(
            classes, base_id=base_id, query=query, top_k=top_k
        )
        return await self._finish_with_rerank(
            fused, query=query, top_k=top_k, model_selection=model_selection
        )

    async def _fused_for_base(
        self,
        classes: list[EmbeddingClass],
        *,
        base_id: str,
        query: str,
        top_k: int,
    ) -> list[RetrievedChunk]:
        if len(classes) == 1:
            return await self._search_one_fused(
                classes[0], base_id=base_id, query=query, top_k=top_k
            )
        return await self._search_many_fused(
            classes, base_id=base_id, query=query, top_k=top_k
        )

    async def _search_spaces(
        self,
        spaces: list[RetrievalSpace],
        *,
        query: str,
        top_k: int,
        model_selection: ModelSelection | None,
        embedder: EmbeddingClient | None,
        embedding_model: str | None,
    ) -> list[RetrievedChunk]:
        """多空间：各空间带自己登记的类集独立走完单空间管线（分数门/相对裁剪/
        per-source 上限在空间内先行），再按名次合并（merge_by_rank，与多类合并
        同一先例），合并后 per-source 上限按全局口径再收一遍，最后统一走一次
        rerank 收尾。类集在空间内解析：某空间的类集解析失败与它的检索失败
        同等对待（跳过并标不完整），不连累其它空间。

        单个空间硬失败（store 异常等）跳过并标不完整；全部空间失败才上抛，
        由 retrieve_chunks 整次降级——与多类「一类失败跳过、全部失败才落空」同口径。
        """
        paths: list[list[RetrievedChunk]] = []
        failures = 0
        last_error: Exception | None = None
        for space in spaces:
            try:
                classes = self._resolve_classes(
                    embedder, space.embedding_classes, embedding_model
                )
                fused = await self._fused_for_base(
                    classes, base_id=space.base_id, query=query, top_k=top_k
                )
            except Exception as exc:  # noqa: BLE001 — 一空间失败跳过，其它空间继续
                failures += 1
                last_error = exc
                logger.warning(
                    "retrieval search failed for base %s (%s); skipping its hits",
                    space.base_id,
                    type(exc).__name__,
                )
                continue
            if fused:
                paths.append(fused)
        if failures == len(spaces) and last_error is not None:
            raise last_error
        if failures:
            self._retrieval_incomplete = True
        merged = apply_per_source_cap(merge_by_rank(paths), self._max_chunks_per_source)
        return await self._finish_with_rerank(
            merged, query=query, top_k=top_k, model_selection=model_selection
        )

    async def search(
        self,
        *,
        spaces: list[RetrievalSpace],
        query: str,
        top_k: int = DEFAULT_TOP_K,
        embedder: EmbeddingClient | None = None,
        model_selection: ModelSelection | None = None,
        embedding_model: str | None = None,
    ) -> list[RetrievedChunk]:
        self._rerank_applied = False
        self._retrieval_incomplete = False
        normalized: list[RetrievalSpace] = []
        seen: set[str] = set()
        for space in spaces:
            base_id = space.base_id.strip()
            if not base_id or base_id in seen:
                continue
            seen.add(base_id)
            normalized.append(
                RetrievalSpace(base_id=base_id, embedding_classes=space.embedding_classes)
            )
        if not normalized:
            return []
        if len(normalized) == 1:
            space = normalized[0]
            classes = self._resolve_classes(
                embedder, space.embedding_classes, embedding_model
            )
            if len(classes) == 1:
                return await self._search_one(
                    classes[0],
                    base_id=space.base_id,
                    query=query,
                    top_k=top_k,
                    model_selection=model_selection,
                )
            return await self._search_many(
                classes,
                base_id=space.base_id,
                query=query,
                top_k=top_k,
                model_selection=model_selection,
            )
        return await self._search_spaces(
            normalized,
            query=query,
            top_k=top_k,
            model_selection=model_selection,
            embedder=embedder,
            embedding_model=embedding_model,
        )

    async def retrieve_chunks(
        self,
        *,
        spaces: list[RetrievalSpace],
        query: str,
        top_k: int = DEFAULT_TOP_K,
        embedder: EmbeddingClient | None = None,
        model_selection: ModelSelection | None = None,
        embedding_model: str | None = None,
    ) -> list[RetrievedChunk]:
        """与 retrieve_context 同一条降级路径：嵌入或向量库失败时返回空列表并置 degraded。

        多类时某一类嵌问题失败只跳过该类，degraded 仍为真，其它类和字面命中照常返回。
        全部类失败才整次降级。chat grounding 与 POST /retrieval/search 都走这里。
        """
        try:
            chunks = await self.search(
                spaces=spaces,
                query=query,
                top_k=top_k,
                embedder=embedder,
                model_selection=model_selection,
                embedding_model=embedding_model,
            )
        except Exception as exc:  # noqa: BLE001
            self._degraded = True
            self._rerank_applied = False
            self._last_error = type(exc).__name__
            logger.warning("retrieval search failed; continuing without context", exc_info=True)
            return []
        self._degraded = self._retrieval_incomplete
        self._last_error = "EmbeddingError" if self._retrieval_incomplete else None
        return chunks

    async def retrieve_context(self, *, base_ids: list[str], query: str) -> list[str]:
        """chat 检索增强：失败降级为无上下文（派生索引问题不阻断对话），记录日志。

        最近一次失败置 degraded 并记下异常类型名；成功则清除。返回值仍是上下文列表。
        空间不带类集：逐空间落进程默认类（retrieve_context 本就没有凭证入参）。
        """
        chunks = await self.retrieve_chunks(
            spaces=[RetrievalSpace(base_id=base_id) for base_id in base_ids], query=query
        )
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
    from agent_services.retrieval.store import PgVectorRetrievalStore

    store = PgVectorRetrievalStore(
        dsn,
        dimensions=settings.ai_embedding_dimensions,
        hnsw_ef_search=settings.retrieval_hnsw_ef_search,
        legacy_embedding_model=settings.ai_embedding_model,
    )
    # 进程没配嵌入模型时仍保留库连接：请求可以带模型入口的 embedding spec 和 key。
    return (
        RetrievalService(
            embedder,
            store,
            min_score=settings.retrieval_min_score,
            score_ratio_floor=settings.retrieval_score_ratio_floor,
            max_chunks_per_source=settings.retrieval_max_chunks_per_source,
            settings=settings,
            embedding_model=settings.ai_embedding_model,
        ),
        None if embedder is not None else blocked_reason,
    )
