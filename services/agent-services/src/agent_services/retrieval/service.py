"""检索服务：分块 → embedding → 向量存取；site_id 全链路强制。"""

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
from agent_services.retrieval.embeddings import EmbeddingClient, create_embedding_client
from agent_services.retrieval.store import RetrievalStore, RetrievedChunk
from agent_services.schemas.chat import ModelSelection

logger = logging.getLogger(__name__)

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


DEFAULT_TOP_K = 4
DEFAULT_MIN_SCORE = RETRIEVAL_MIN_SCORE_DEFAULT
DEFAULT_SCORE_RATIO_FLOOR = RETRIEVAL_SCORE_RATIO_FLOOR_DEFAULT
MAX_UPSERT_CHUNKS = 256
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
            chosen[key] = RetrievedChunk(hit.source_id, hit.chunk_index, hit.content, coverage)
            continue
        chosen[key] = RetrievedChunk(
            previous.source_id,
            previous.chunk_index,
            previous.content,
            max(previous.score, coverage),
        )
    return sorted(chosen.values(), key=lambda item: (-item.score, item.source_id, item.chunk_index))


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
    ) -> None:
        self._embedder = embedder
        self._store = store
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

    async def upsert_document(
        self,
        *,
        site_id: str,
        source_id: str,
        title: str,
        content: str,
        embedder: EmbeddingClient | None = None,
    ) -> int:
        chunks = split_text(content)
        if not chunks:
            await self._store.delete_document(site_id=site_id, source_id=source_id)
            return 0
        if len(chunks) > MAX_UPSERT_CHUNKS:
            raise RetrievalChunkLimitExceeded(
                f"Document exceeds the maximum of {MAX_UPSERT_CHUNKS} chunks."
            )
        client = embedder or self._embedder
        if client is None:
            raise RuntimeError("Embedding client is not configured.")
        embeddings = await client.embed_documents(chunks)
        return await self._store.upsert_document(
            site_id=site_id,
            source_id=source_id,
            title=title,
            chunks=chunks,
            embeddings=embeddings,
        )

    async def prepare_reembed(
        self,
        *,
        embedder: EmbeddingClient | None = None,
        confirm_recreate: bool = False,
    ) -> ReembedPrepared:
        """按 upsert 的 embedder 回退解析探针维度，必要时建表或重建。"""
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

    async def delete_document(self, *, site_id: str, source_id: str) -> None:
        await self._store.delete_document(site_id=site_id, source_id=source_id)

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

    async def search(
        self,
        *,
        site_id: str,
        query: str,
        top_k: int = DEFAULT_TOP_K,
        embedder: EmbeddingClient | None = None,
        model_selection: ModelSelection | None = None,
    ) -> list[RetrievedChunk]:
        self._rerank_applied = False
        client = embedder or self._embedder
        if client is None:
            raise RuntimeError("Embedding client is not configured.")
        embedding = await client.embed_query(query)
        vector_hits = apply_min_score(
            await self._store.search(site_id=site_id, embedding=embedding, top_k=top_k),
            self._min_score,
        )
        terms = query_terms(query)
        lexical_hits: list[RetrievedChunk] = []
        # 上面的嵌入和向量查询若失败会直接抛出，词法查询不会先跑。
        if terms:
            lexical_hits = await self._store.search_lexical(
                site_id=site_id,
                tokens=terms,
                limit=LEXICAL_CANDIDATE_LIMIT,
            )
        fused = apply_per_source_cap(
            apply_score_ratio_floor(
                _ranked_hits(vector_hits, lexical_hits, terms), self._score_ratio_floor
            ),
            self._max_chunks_per_source,
        )
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

    async def retrieve_chunks(
        self,
        *,
        site_id: str,
        query: str,
        top_k: int = DEFAULT_TOP_K,
        embedder: EmbeddingClient | None = None,
        model_selection: ModelSelection | None = None,
    ) -> list[RetrievedChunk]:
        """与 retrieve_context 同一条降级路径：嵌入或向量库失败时返回空列表并置 degraded。

        chat grounding 与 POST /retrieval/search 都走这里，避免另写一套错误格式。
        """
        try:
            chunks = await self.search(
                site_id=site_id,
                query=query,
                top_k=top_k,
                embedder=embedder,
                model_selection=model_selection,
            )
        except Exception as exc:  # noqa: BLE001
            self._degraded = True
            self._rerank_applied = False
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
    from agent_services.retrieval.store import PgVectorRetrievalStore

    store = PgVectorRetrievalStore(
        dsn,
        dimensions=settings.ai_embedding_dimensions,
        hnsw_ef_search=settings.retrieval_hnsw_ef_search,
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
        ),
        None if embedder is not None else blocked_reason,
    )
