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
from agent_services.retrieval.service import RetrievalService
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import ModelSelection

logger = logging.getLogger(__name__)

SEARCH_TOOL_NAME = "search_site_knowledge"


def format_knowledge_chunks(chunks: list[RetrievedChunk]) -> str:
    """与 chat grounding 一样用 `- ` 片段；工具返回另附来源标识。"""
    if not chunks:
        return "No matching site knowledge."
    return "\n\n".join(
        f"- {chunk.content} (source: {chunk.source_id}#{chunk.chunk_index})" for chunk in chunks
    )


@dataclass
class KnowledgeSearchCollector:
    """一次 run 内知识搜索的回流收集器：命中片段、逐次摘要与降级标记。

    runtime 在 run 结束后读它生成 citations 与 retrievalDegraded；
    流式路径还用它给 TOOL_RESULT 附安全摘要（命中数与来源，不带正文）。
    """

    chunks: list[RetrievedChunk] = field(default_factory=list)
    searches: list[dict[str, object]] = field(default_factory=list)
    degraded: bool = False

    def record(self, query: str, chunks: list[RetrievedChunk]) -> dict[str, object]:
        seen = {(chunk.source_id, chunk.chunk_index) for chunk in self.chunks}
        for chunk in chunks:
            key = (chunk.source_id, chunk.chunk_index)
            if key not in seen:
                seen.add(key)
                self.chunks.append(chunk)
        summary: dict[str, object] = {
            "query": query,
            "hitCount": len(chunks),
            "sources": [
                {"sourceId": chunk.source_id, "chunkIndex": chunk.chunk_index, "score": chunk.score}
                for chunk in chunks
            ],
        }
        self.searches.append(summary)
        return summary

    def summary_for(self, call_index: int) -> dict[str, object] | None:
        """第 n 次（1 起）搜索调用的摘要；不存在时返回 None。"""
        if 1 <= call_index <= len(self.searches):
            return self.searches[call_index - 1]
        return None


def build_knowledge_toolset(
    site_id: str,
    retrieval: RetrievalService | None,
    *,
    embedder: EmbeddingClient | None = None,
    model_selection: ModelSelection | None = None,
    collector: KnowledgeSearchCollector | None = None,
) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain(name=SEARCH_TOOL_NAME)
    async def search_site_knowledge(query: str) -> str:
        """Search this site's knowledge base and return matching passages with their source ids.

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
                site_id=site_id,
                query=cleaned,
                embedder=embedder,
                model_selection=model_selection,
            )
        except Exception as exc:  # noqa: BLE001 — 检索失败不让本轮 run 失败
            logger.warning("knowledge search failed", exc_info=exc)
            if collector is not None:
                collector.degraded = True
            return "Site knowledge search failed."
        if retrieval.degraded:
            if collector is not None:
                collector.degraded = True
            return "Site knowledge search failed."
        if collector is not None:
            collector.record(cleaned, chunks)
        return format_knowledge_chunks(chunks)

    return toolset
