"""站点知识搜索工具：主 agent 与研究子 agent 共用同一份实现。

检索失败只变成工具返回文本，不让本轮 run 因此中断；检索服务缺失或降级时如实说明。
知识检索由 agent 在 run 内执行（工具调用），runtime 不再预取注入：
工具命中的片段经 KnowledgeSearchCollector 回流，run 结束时据此生成 citations。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

from pydantic_ai.toolsets import FunctionToolset

from agent_services.retrieval.embeddings import EmbeddingClient
from agent_services.retrieval.service import EmbeddingClass, RetrievalService
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import ModelSelection

logger = logging.getLogger(__name__)

SEARCH_TOOL_NAME = "search_site_knowledge"

# 工具原文给模型看，与 harness 一样用英文。用户可见的降级说明另按 locale 追加。
DEGRADED_TOOL_LINE = (
    "Search degraded: at least one embedding class failed. The passages below are incomplete."
)


def format_knowledge_chunks(
    chunks: list[RetrievedChunk] | list[tuple[int, RetrievedChunk]],
    *,
    degraded: bool = False,
) -> str:
    """片段前标 [cN]。编号由调用方传入；只传片段时从 1 顺序编号。"""
    numbered = _numbered_chunks(chunks)
    if not numbered:
        return "No matching site knowledge."
    lines = [DEGRADED_TOOL_LINE] if degraded else []
    lines.extend(
        f"- [c{cite}] {chunk.content} ({_chunk_suffix(chunk)})" for cite, chunk in numbered
    )
    return "\n\n".join(lines)


def _numbered_chunks(
    chunks: list[RetrievedChunk] | list[tuple[int, RetrievedChunk]],
) -> list[tuple[int, RetrievedChunk]]:
    if not chunks:
        return []
    first = chunks[0]
    if isinstance(first, tuple):
        return [(cite, chunk) for cite, chunk in chunks]  # type: ignore[misc]
    return [(chunk.cite if chunk.cite is not None else index, chunk) for index, chunk in enumerate(chunks, start=1)]  # type: ignore[arg-type]


def _chunk_suffix(chunk: RetrievedChunk) -> str:
    parts: list[str] = []
    if chunk.title:
        parts.append(f"title: {chunk.title}")
    parts.append(f"source: {chunk.source_id}#{chunk.chunk_index}")
    if chunk.location is not None:
        meta = chunk.location.to_api()
        if "page" in meta:
            parts.append(f"page: {meta['page']}")
        if "sheet" in meta:
            parts.append(f"sheet: {meta['sheet']}")
        if "rowStart" in meta or "rowEnd" in meta:
            start = meta.get("rowStart", "")
            end = meta.get("rowEnd", "")
            parts.append(f"rows: {start}-{end}")
    return ", ".join(parts)


@dataclass
class KnowledgeSearchCollector:
    """一次 run 内知识搜索的回流收集器：命中片段、逐次摘要与降级标记。

    runtime 在 run 结束后读它生成 citations 与 retrievalDegraded；
    流式路径还用它给 TOOL_RESULT 附安全摘要（命中数与来源，不带正文）。
    """

    chunks: list[RetrievedChunk] = field(default_factory=list)
    searches: list[dict[str, object]] = field(default_factory=list)
    degraded: bool = False
    _cites: dict[tuple[str, int], int] = field(default_factory=dict)

    def record(self, query: str, chunks: list[RetrievedChunk]) -> list[tuple[int, RetrievedChunk]]:
        """按累积顺序分配 [cN]。同一片段再次命中沿用原编号，不按分数重排。"""
        numbered: list[tuple[int, RetrievedChunk]] = []
        for chunk in chunks:
            key = (chunk.source_id, chunk.chunk_index)
            cite = self._cites.get(key)
            if cite is None:
                cite = len(self.chunks) + 1
                stored = chunk.with_cite(cite)
                self._cites[key] = cite
                self.chunks.append(stored)
            else:
                stored = self.chunks[cite - 1]
            numbered.append((cite, stored))
        summary: dict[str, object] = {
            "query": query,
            "hitCount": len(chunks),
            "sources": [
                {"sourceId": chunk.source_id, "chunkIndex": chunk.chunk_index, "score": chunk.score}
                for chunk in chunks
            ],
        }
        self.searches.append(summary)
        return numbered

    def summary_for(self, call_index: int) -> dict[str, object] | None:
        """第 n 次（1 起）搜索调用的摘要；不存在时返回 None。"""
        if 1 <= call_index <= len(self.searches):
            return self.searches[call_index - 1]
        return None


def build_knowledge_toolset(
    agent_id: str,
    retrieval: RetrievalService | None,
    *,
    embedder: EmbeddingClient | None = None,
    embedding_model: str | None = None,
    embedding_classes: list[EmbeddingClass] | None = None,
    model_selection: ModelSelection | None = None,
    collector: KnowledgeSearchCollector | None = None,
) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain(name=SEARCH_TOOL_NAME)
    async def search_site_knowledge(query: str) -> str:
        """Search this site's knowledge base and return matching passages.

        Each passage is labeled [cN]. Cite that label inline. The label is the collector order, not a rank.

        Args:
            query: what to look up in the site knowledge base.
        """
        cleaned = query.strip()
        if not cleaned:
            return "query is required."
        if retrieval is None:
            return "Site knowledge search is unavailable."
        try:
            chunks = await retrieval.retrieve_chunks(
                agent_id=agent_id,
                query=cleaned,
                embedder=embedder,
                embedding_model=embedding_model,
                embedding_classes=embedding_classes,
                model_selection=model_selection,
            )
        except Exception as exc:  # noqa: BLE001 — 检索失败不让本轮 run 失败
            logger.warning("knowledge search failed", exc_info=exc)
            if collector is not None:
                collector.degraded = True
            return "Site knowledge search failed."
        if retrieval.degraded and collector is not None:
            collector.degraded = True
        # 某一类失败时 degraded 为真，但其它类和字面命中仍然返回。
        if retrieval.degraded and not chunks:
            return "Site knowledge search failed."
        numbered: list[tuple[int, RetrievedChunk]] | list[RetrievedChunk]
        if collector is not None:
            numbered = collector.record(cleaned, chunks)
        else:
            numbered = chunks
        return format_knowledge_chunks(numbered, degraded=bool(retrieval.degraded and chunks))

    return toolset
