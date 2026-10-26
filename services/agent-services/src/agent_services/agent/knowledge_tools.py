"""站点知识搜索工具：主 agent 与研究子 agent 共用同一份实现。

检索失败只变成工具返回文本，不让本轮 run 因此中断；检索服务缺失或降级时如实说明。
"""

from __future__ import annotations

import logging

from pydantic_ai.toolsets import FunctionToolset

from agent_services.retrieval.service import RetrievalService
from agent_services.retrieval.store import RetrievedChunk

logger = logging.getLogger(__name__)


def format_knowledge_chunks(chunks: list[RetrievedChunk]) -> str:
    """与 chat grounding 一样用 `- ` 片段；工具返回另附来源标识。"""
    if not chunks:
        return "No matching site knowledge."
    return "\n\n".join(
        f"- {chunk.content} (source: {chunk.source_id}#{chunk.chunk_index})" for chunk in chunks
    )


def build_knowledge_toolset(site_id: str, retrieval: RetrievalService | None) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain
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
            chunks = await retrieval.retrieve_chunks(site_id=site_id, query=cleaned)
        except Exception as exc:  # noqa: BLE001 — 检索失败不让本轮 run 失败
            logger.warning("knowledge search failed", exc_info=exc)
            return "Site knowledge search failed."
        if retrieval.degraded:
            return "Site knowledge search failed."
        return format_knowledge_chunks(chunks)

    return toolset
