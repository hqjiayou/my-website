"""站点结构化表查询。只在该站已有结构化行时由 toolset 工厂注册。"""

from __future__ import annotations

import logging

from pydantic_ai.toolsets import FunctionToolset

from agent_services.agent.knowledge_tools import KnowledgeSearchCollector
from agent_services.retrieval.tables import StoredSheet, run_table_query

logger = logging.getLogger(__name__)

TABLE_TOOL_NAME = "query_site_table"


async def structured_tables_available(
    retrieval: object | None, base_ids: list[str]
) -> bool:
    """任一空间有结构化行即为真。探测失败当成没有表，避免对话因为检索库不可用而 500。"""
    if retrieval is None:
        return False
    probe = getattr(retrieval, "has_structured_tables", None)
    if probe is None:
        return False
    try:
        for base_id in base_ids:
            if await probe(base_id):
                return True
        return False
    except Exception:
        logger.warning("structured table probe failed", exc_info=True)
        return False


def build_table_toolset(
    base_ids: list[str],
    retrieval: object | None,
    *,
    collector: KnowledgeSearchCollector | None = None,
) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain(name=TABLE_TOOL_NAME)
    async def query_site_table(
        op: str,
        source_id: str = "",
        sheet: str = "",
        column: str = "",
        match: str = "",
        value: str = "",
        low: str = "",
        high: str = "",
        aggregate: str = "",
    ) -> str:
        """Query one structured table that was indexed for this site.

        Use list to see document titles, sheets, and column names.
        Use filter with match eq, contains, or range.
        Use aggregate with count, sum, avg, min, or max.
        Column must be one of that sheet's headers. This tool does not accept SQL and does not evaluate formulas.

        Args:
            op: list, filter, or aggregate.
            source_id: document source id from search_site_knowledge, such as kb:14.
            sheet: sheet name from list.
            column: exact header text.
            match: eq, contains, or range.
            value: comparison text for eq or contains.
            low: inclusive lower bound for range.
            high: inclusive upper bound for range.
            aggregate: count, sum, avg, min, or max.
        """
        if retrieval is None:
            text = "No structured table data is available for this site."
        else:
            try:
                loaded: list[object] = []
                for base_id in base_ids:
                    loaded.extend(
                        await retrieval.load_structured_tables(base_id)  # type: ignore[attr-defined]
                    )
                sheets = [item for item in loaded if isinstance(item, StoredSheet)]
            except Exception:
                logger.warning("structured table load failed", exc_info=True)
                text = "Structured table query failed."
            else:
                text = run_table_query(
                    sheets,
                    op=op,
                    source_id=source_id,
                    sheet=sheet,
                    column=column,
                    match=match,
                    value=value,
                    low=low,
                    high=high,
                    aggregate=aggregate,
                )
        if collector is not None:
            collector.record_passage(text)
        return text

    return toolset
