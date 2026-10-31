"""结构化表查询：入库替换、封闭操作、以及汇总数字进入 #156 语料后保留。"""

from conftest import make_settings
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.usage import UsageLimits
from test_knowledge_tools import _tool_names
from test_retrieval import FakeEmbedder, FakeStore

from agent_services.agent.harness import assemble_harness
from agent_services.agent.knowledge_tools import KnowledgeSearchCollector
from agent_services.agent.runtime import (
    ChatReply,
    RunBudget,
    plan_search_grounding,
    run_chat,
)
from agent_services.agent.table_tools import (
    build_table_toolset,
    structured_tables_available,
)
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.retrieval.service import (
    EmbeddingClass,
    IndexTable,
    IndexTableRow,
    RetrievalService,
)
from agent_services.retrieval.tables import MAX_SCAN_ROWS, StoredSheet, run_table_query
from agent_services.schemas.retrieval import RetrievalUpsertRequest


def _sheet() -> StoredSheet:
    return StoredSheet(
        source_id="kb:14",
        title="勤務管理表2026",
        sheet="10月",
        header_row=19,
        headers=["Day", "Hours", "Amount", "Note"],
        rows=[
            (20, ["Mon", "8:00", "10", "ok"]),
            (21, ["Tue", "1:30", "2.5", "ok"]),
            (22, ["Wed", "", "", "n/a"]),
        ],
    )


def _index_table(sheet: str = "10月", hours: str = "8:00") -> IndexTable:
    return IndexTable(
        sheet=sheet,
        header_row=19,
        headers=["Day", "Hours"],
        rows=[IndexTableRow(row_number=20, cells=["Mon", hours])],
    )


class _Loader:
    def __init__(self, sheets: list[StoredSheet]) -> None:
        self.sheets = sheets

    async def load_structured_tables(self, agent_id: str) -> list[StoredSheet]:
        assert agent_id == "1"
        return self.sheets


class _Boom:
    async def has_structured_tables(self, agent_id: str) -> bool:
        raise RuntimeError("db down")


class _Embedder:
    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[0.1, 0.2] for _ in texts]


async def _call(toolset: object, **kwargs: str) -> str:
    tool = toolset.tools["query_site_table"]  # type: ignore[attr-defined]
    return await tool.function(**kwargs)


def test_list_names_title_sheet_and_columns() -> None:
    text = run_table_query([_sheet()], op="list")
    assert "勤務管理表2026" in text
    assert "10月" in text
    assert "kb:14" in text
    assert "Hours" in text


def test_filter_eq_returns_source_and_row_span() -> None:
    text = run_table_query(
        [_sheet()],
        op="filter",
        source_id="kb:14",
        sheet="10月",
        column="Day",
        match="eq",
        value="Mon",
    )
    assert "Document: 勤務管理表2026 (source: kb:14)" in text
    assert "Sheet: 10月" in text
    assert "Rows: 20-20" in text
    assert "8:00" in text
    assert "Tue" not in text


def test_filter_contains_and_numeric_range() -> None:
    contains = run_table_query(
        [_sheet()], op="filter", sheet="10月", column="Note", match="contains", value="OK"
    )
    assert "Rows: 20-21" in contains
    ranged = run_table_query(
        [_sheet()], op="filter", sheet="10月", column="Amount", match="range", low="1", high="10"
    )
    assert "Rows: 20-21" in ranged
    assert "Wed" not in ranged


def test_aggregate_count() -> None:
    text = run_table_query([_sheet()], op="aggregate", sheet="10月", aggregate="count")
    assert "count: 3" in text
    assert "Rows: 20-22" in text


def test_aggregate_numeric_sum() -> None:
    text = run_table_query(
        [_sheet()], op="aggregate", sheet="10月", column="Amount", aggregate="sum"
    )
    assert "sum: 12.5" in text


def test_aggregate_duration_sum() -> None:
    text = run_table_query(
        [_sheet()], op="aggregate", sheet="10月", column="Hours", aggregate="sum"
    )
    assert "sum: 9.5" in text


def test_non_aggregable_column_is_stated() -> None:
    text = run_table_query(
        [_sheet()], op="aggregate", sheet="10月", column="Note", aggregate="sum"
    )
    assert "cannot be aggregated" in text
    assert "sum:" not in text


def test_unknown_column_is_rejected() -> None:
    text = run_table_query(
        [_sheet()], op="aggregate", sheet="10月", column="Nope", aggregate="sum"
    )
    assert "Nope" in text
    assert "is not in this sheet" in text


def test_over_scan_is_stated() -> None:
    huge = StoredSheet(
        source_id="kb:14",
        title="勤務管理表2026",
        sheet="10月",
        header_row=1,
        headers=["Hours"],
        rows=[(index, ["1"]) for index in range(1, MAX_SCAN_ROWS + 2)],
    )
    text = run_table_query([huge], op="aggregate", sheet="10月", column="Hours", aggregate="sum")
    assert "The result was not computed." in text
    assert "sum:" not in text
    truncated = StoredSheet(
        source_id="kb:14",
        title="勤務管理表2026",
        sheet="10月",
        header_row=1,
        headers=["Hours"],
        rows=[(2, ["8:00"])],
        truncated=True,
    )
    refused = run_table_query(
        [truncated], op="aggregate", sheet="10月", column="Hours", aggregate="sum"
    )
    assert "The result was not computed." in refused


def test_named_document_without_rows_says_no_structured_data() -> None:
    text = run_table_query([_sheet()], op="aggregate", source_id="kb:99", sheet="10月", aggregate="count")
    assert text == "No structured table data is available for that document."


def test_upsert_request_accepts_tables_and_omits_them() -> None:
    body = RetrievalUpsertRequest.model_validate(
        {
            "agentId": "1",
            "sourceId": "kb:14",
            "content": "alpha",
            "tables": [
                {
                    "sheet": "10月",
                    "headerRow": 19,
                    "headers": ["Hours"],
                    "rows": [{"rowNumber": 20, "cells": ["8:00"]}],
                }
            ],
        }
    )
    assert body.tables is not None
    assert body.tables[0].rows[0].row_number == 20
    omitted = RetrievalUpsertRequest.model_validate(
        {"agentId": "1", "sourceId": "kb:14", "content": "alpha"}
    )
    assert omitted.tables is None


async def test_upsert_with_tables_replaces_rows_for_source() -> None:
    store = FakeStore()
    service = RetrievalService(FakeEmbedder(), store)
    await service.upsert_document(
        agent_id="1",
        source_id="kb:14",
        title="勤務管理表2026",
        content="alpha facts",
        tables=[_index_table("10月"), _index_table("11月", "9:00")],
    )
    await service.upsert_document(
        agent_id="1",
        source_id="kb:14",
        title="勤務管理表2026",
        content="alpha facts",
        tables=[_index_table("10月", "7:30")],
    )
    stored = store.sheets[("1", "kb:14")]
    assert [table.sheet for table in stored] == ["10月"]
    assert stored[0].rows[0].cells == ["Mon", "7:30"]
    assert store.docs[("1", "kb:14")] == ["alpha facts"]


async def test_upsert_without_tables_matches_plain_chunks_and_clears_rows() -> None:
    store = FakeStore()
    service = RetrievalService(FakeEmbedder(), store)
    await service.upsert_document(
        agent_id="1",
        source_id="kb:14",
        title="t",
        content="alpha facts",
        tables=[_index_table()],
    )
    plain_store = FakeStore()
    plain = RetrievalService(FakeEmbedder(), plain_store)
    await plain.upsert_document(agent_id="1", source_id="kb:14", title="t", content="alpha facts")
    await service.upsert_document(agent_id="1", source_id="kb:14", title="t", content="alpha facts")
    assert store.docs[("1", "kb:14")] == plain_store.docs[("1", "kb:14")]
    assert store.sheets[("1", "kb:14")] == []


async def test_multi_class_upsert_replaces_sheet_rows_once() -> None:
    store = FakeStore()
    service = RetrievalService(None, store)
    await service.upsert_document(
        agent_id="1",
        source_id="kb:14",
        title="t",
        content="alpha facts",
        embedding_classes=[
            EmbeddingClass("openai:a", _Embedder()),
            EmbeddingClass("openai:b", _Embedder()),
        ],
        tables=[_index_table()],
    )
    assert store.sheet_replaces == [("1", "kb:14")]
    assert store.docs[("1", "kb:14")] == ["alpha facts"]


def test_table_tool_registered_only_when_tables_available() -> None:
    present = build_execution_toolsets(
        make_settings(),
        "1",
        todo_store=TodoStore(),
        retrieval=object(),  # type: ignore[arg-type]
        tables_available=True,
    )
    absent = build_execution_toolsets(
        make_settings(),
        "1",
        todo_store=TodoStore(),
        retrieval=object(),  # type: ignore[arg-type]
    )
    assert "query_site_table" in _tool_names(present)
    assert "search_site_knowledge" in _tool_names(present)
    assert "query_site_table" not in _tool_names(absent)


async def test_structured_table_probe_failure_is_unavailable() -> None:
    assert await structured_tables_available(None, "1") is False
    assert await structured_tables_available(_Boom(), "1") is False


async def test_table_tool_records_result_text() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_table_toolset("1", _Loader([_sheet()]), collector=collector)
    text = await _call(
        toolset,
        op="aggregate",
        source_id="kb:14",
        sheet="10月",
        column="Hours",
        aggregate="sum",
    )
    assert "sum: 9.5" in text
    assert collector.passages == [text]
    assert collector.chunks == []


def test_aggregate_gate_still_replaces_number_absent_from_passages() -> None:
    action, reply = plan_search_grounding(
        "2026年10月总工时合计是多少",
        "十月合计是 9.5 小时",
        KnowledgeSearchCollector(),
        "zh-CN",
        allow_retry=False,
    )
    assert action == "aggregate"
    assert reply == "片段检索无法给出全量统计。"


async def test_table_total_is_kept_in_done_reply() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_table_toolset("1", _Loader([_sheet()]), collector=collector)

    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen_tool = any(
            type(part).__name__ == "ToolReturnPart" for message in messages for part in message.parts
        )
        if not seen_tool:
            return ModelResponse(
                parts=[
                    ToolCallPart(
                        tool_name="query_site_table",
                        args={
                            "op": "aggregate",
                            "source_id": "kb:14",
                            "sheet": "10月",
                            "column": "Hours",
                            "aggregate": "sum",
                        },
                    )
                ]
            )
        return ModelResponse(parts=[TextPart("十月合计是 9.5 小时")])

    outcome = await run_chat(
        FunctionModel(respond),
        agent_id="1",
        message="2026年10月总工时合计是多少",
        locale="zh-CN",
        toolsets=[toolset],  # type: ignore[list-item]
        knowledge_collector=collector,
        budget=RunBudget(usage_limits=UsageLimits(request_limit=4, tool_calls_limit=4)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "十月合计是 9.5 小时"
    assert "片段检索无法给出全量统计" not in outcome.reply


def test_assembled_prompt_routes_period_stats_through_table_query() -> None:
    harness = assemble_harness(agent_id="1", message="十月出勤合计")
    prompt = harness.system_prompt
    assert "search_site_knowledge to locate the document" in prompt
    assert "query_site_table" in prompt
    assert "only when that number appears in the tool output" in prompt
    assert "passage search cannot support a full total" in prompt
