"""检索服务：分块 → embedding → 向量存取；site_id 全链路强制。"""

import logging
import re

from agent_services.config import RETRIEVAL_MIN_SCORE_DEFAULT, Settings
from agent_services.retrieval.chunking import split_text
from agent_services.retrieval.embeddings import EmbeddingClient, create_embedding_client
from agent_services.retrieval.store import RetrievalStore, RetrievedChunk

logger = logging.getLogger(__name__)

DEFAULT_TOP_K = 4
DEFAULT_MIN_SCORE = RETRIEVAL_MIN_SCORE_DEFAULT
MAX_UPSERT_CHUNKS = 256
# 与检索 topK 上限一致：代码通道在向量窗口之外另取候选，再和向量结果一起截到 top_k。
LEXICAL_CANDIDATE_LIMIT = 20
MAX_CODE_TOKENS = 32

# 连字符/点号型号整段保留，例如 XJ-900、v1.2。
_MODEL_TOKEN = re.compile(r"[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)+")
_ALNUM_TOKEN = re.compile(r"[A-Za-z0-9]+")


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


def code_coverage(content: str, tokens: list[str]) -> float:
    """命中的 token 数 / token 总数。比较时忽略 ASCII 大小写。"""
    if not tokens:
        return 0.0
    folded = content.casefold()
    matched = sum(1 for token in tokens if token.casefold() in folded)
    return matched / len(tokens)


def merge_code_hits(
    vector_hits: list[RetrievedChunk],
    lexical_hits: list[RetrievedChunk],
    tokens: list[str],
    *,
    top_k: int,
) -> list[RetrievedChunk]:
    """向量命中与代码命中按 (source_id, chunk_index) 合并。

    只在向量侧的分数保持余弦。代码通道命中的分数是覆盖率。两边都有时取较大值。
    覆盖率为 0 的词法行丢弃。结果按分数降序截到 top_k。
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
    ranked = sorted(chosen.values(), key=lambda item: (-item.score, item.source_id, item.chunk_index))
    return ranked[:top_k]


class RetrievalService:
    def __init__(
        self,
        embedder: EmbeddingClient | None,
        store: RetrievalStore,
        *,
        min_score: float = DEFAULT_MIN_SCORE,
    ) -> None:
        self._embedder = embedder
        self._store = store
        self._min_score = min_score
        self._degraded = False
        self._last_error: str | None = None

    @property
    def has_process_embedder(self) -> bool:
        return self._embedder is not None

    @property
    def degraded(self) -> bool:
        return self._degraded

    @property
    def last_error(self) -> str | None:
        return self._last_error

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

    async def delete_document(self, *, site_id: str, source_id: str) -> None:
        await self._store.delete_document(site_id=site_id, source_id=source_id)

    async def search(
        self,
        *,
        site_id: str,
        query: str,
        top_k: int = DEFAULT_TOP_K,
        embedder: EmbeddingClient | None = None,
    ) -> list[RetrievedChunk]:
        client = embedder or self._embedder
        if client is None:
            raise RuntimeError("Embedding client is not configured.")
        embedding = await client.embed_query(query)
        vector_hits = apply_min_score(
            await self._store.search(site_id=site_id, embedding=embedding, top_k=top_k),
            self._min_score,
        )
        tokens = code_tokens(query)
        lexical_hits: list[RetrievedChunk] = []
        # 上面的嵌入和向量查询若失败会直接抛出，词法查询不会先跑。
        if tokens:
            lexical_hits = await self._store.search_lexical(
                site_id=site_id,
                tokens=tokens,
                limit=LEXICAL_CANDIDATE_LIMIT,
            )
        return merge_code_hits(vector_hits, lexical_hits, tokens, top_k=top_k)

    async def retrieve_chunks(
        self,
        *,
        site_id: str,
        query: str,
        top_k: int = DEFAULT_TOP_K,
        embedder: EmbeddingClient | None = None,
    ) -> list[RetrievedChunk]:
        """与 retrieve_context 同一条降级路径：嵌入或向量库失败时返回空列表并置 degraded。

        chat grounding 与 POST /retrieval/search 都走这里，避免另写一套错误格式。
        """
        try:
            chunks = await self.search(site_id=site_id, query=query, top_k=top_k, embedder=embedder)
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
    from agent_services.retrieval.store import PgVectorRetrievalStore

    store = PgVectorRetrievalStore(dsn, dimensions=settings.ai_embedding_dimensions)
    # 进程没配嵌入模型时仍保留库连接：请求可以带模型入口的 embedding spec 和 key。
    return (
        RetrievalService(embedder, store, min_score=settings.retrieval_min_score),
        None if embedder is not None else blocked_reason,
    )
