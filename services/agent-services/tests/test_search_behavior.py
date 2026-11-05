"""Issue #156：标题回读、引用编号、跑完校验与黄金题。不调用真供应商。"""

from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.usage import UsageLimits
from test_retrieval import FakeEmbedder, FakeStore

from agent_services.agent.knowledge_tools import (
    DEGRADED_TOOL_LINE,
    KnowledgeSearchCollector,
    build_knowledge_toolset,
    format_knowledge_chunks,
)
from agent_services.agent.runtime import (
    ChatReply,
    RunBudget,
    plan_search_grounding,
    run_chat,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.retrieval.service import (
    IndexSegment,
    RetrievalService,
    _ranked_hits,
)
from agent_services.retrieval.store import (
    RetrievedChunk,
    chunk_from_row,
    citation_sources,
    lexical_search_statement,
    vector_search_sql,
)
from agent_services.schemas.stream import AgentStreamEventType


def test_query_sql_selects_metadata() -> None:
    assert "metadata" in vector_search_sql("chunks")
    sql, _args = lexical_search_statement("1", ["考勤"], 4)
    assert "metadata" in sql


def test_stored_title_reaches_tool_text_and_citations_without_reembed() -> None:
    """入库行只有 metadata.title。查询还原后工具返回与 citations 都带这个标题。"""
    chunk = chunk_from_row(
        source_id="kb:14",
        chunk_index=0,
        content="十月出勤",
        score=0.8,
        metadata='{"title": "10月考勤表"}',
    )
    assert chunk.title == "10月考勤表"
    collector = KnowledgeSearchCollector()
    numbered = collector.record("考勤", [chunk])
    text = format_knowledge_chunks(numbered)
    assert "[c1]" in text
    assert "title: 10月考勤表" in text
    citations = citation_sources(collector.chunks)
    assert citations[0]["title"] == "10月考勤表"
    assert citations[0]["cite"] == 1
    assert citations[0]["sourceId"] == "kb:14"


def test_score_merge_keeps_title_and_location() -> None:
    titled = RetrievedChunk(
        "kb:1",
        0,
        "十月出勤",
        0.4,
        title="10月考勤表",
        location=None,
    )
    lexical = RetrievedChunk("kb:1", 0, "十月出勤", 0.1)
    merged = _ranked_hits([titled], [lexical], ["十月"])
    assert merged[0].title == "10月考勤表"
    assert merged[0].content == "十月出勤"


def test_citation_numbers_follow_collector_order_when_score_truncates() -> None:
    chunks = [
        RetrievedChunk("kb:1", index, f"row {index}", score, title="10月考勤表" if index == 0 else None, cite=index + 1)
        for index, score in enumerate((0.9, 0.2, 0.8, 0.7, 0.6, 0.01))
    ]
    citations = citation_sources(chunks, limit=5)
    assert [item["cite"] for item in citations] == [1, 2, 3, 4, 5]
    assert citations[0]["title"] == "10月考勤表"


def test_degraded_tool_line_leads_partial_hits() -> None:
    text = format_knowledge_chunks(
        [(1, RetrievedChunk("kb:1", 0, "十月出勤", 0.5, title="10月考勤表"))],
        degraded=True,
    )
    assert text.startswith(DEGRADED_TOOL_LINE)
    assert "title: 10月考勤表" in text


class _RecordingStore(FakeStore):
    def __init__(self) -> None:
        super().__init__()
        self.locations = None
        self.seen_chunks: list[str] = []

    async def upsert_document(self, *, base_id, source_id, title, chunks, embeddings, embedding_model="", locations=None):
        self.seen_chunks = list(chunks)
        self.locations = locations
        return await super().upsert_document(
            base_id=base_id,
            source_id=source_id,
            title=title,
            chunks=chunks,
            embeddings=embeddings,
            embedding_model=embedding_model,
        )


async def test_upsert_without_segments_matches_plain_split() -> None:
    service, store = _service()
    count = await service.upsert_document(base_id="1", source_id="kb:1", title="t", content="alpha facts")
    assert count == 1
    assert store.docs[("1", "kb:1")] == ["alpha facts"]


async def test_upsert_segments_split_inside_each_segment() -> None:
    store = _RecordingStore()
    service = RetrievalService(FakeEmbedder(), store)
    count = await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="考勤",
        content="this whole document is not what gets indexed",
        segments=[
            IndexSegment(text="sheet body", sheet="Hours", row_start=2, row_end=5),
            IndexSegment(text="page body", page=3),
        ],
    )
    assert count == 2
    assert store.seen_chunks == ["sheet body", "page body"]
    assert store.locations is not None
    assert store.locations[0].sheet == "Hours"
    assert store.locations[0].row_start == 2
    assert store.locations[0].row_end == 5
    assert store.locations[1].page == 3


def _service():
    store = FakeStore()
    return RetrievalService(FakeEmbedder(), store), store


class _StubRetrieval:
    def __init__(self, *, degraded: bool = False, content: str = "十月出勤记录") -> None:
        self.degraded = degraded
        self.content = content

    async def retrieve_chunks(self, **_kwargs: object) -> list[RetrievedChunk]:
        return [RetrievedChunk("kb:14", 0, self.content, 0.9, title="10月考勤表")]


def _search_then(text: str, calls: list[int]):
    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls.append(1)
        seen_tool = any(
            isinstance(part, ToolCallPart) or type(part).__name__ == "ToolReturnPart"
            for message in messages
            for part in message.parts
        )
        if not seen_tool:
            return ModelResponse(parts=[ToolCallPart(tool_name="search_site_knowledge", args={"query": "考勤"})])
        return ModelResponse(parts=[TextPart(text)])

    return respond


async def test_fake_model_record_question_without_search_is_replaced() -> None:
    calls: list[str] = []

    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls.append("run")
        return ModelResponse(parts=[TextPart("申请编号 A123456 状态待送件")])

    outcome = await run_chat(
        FunctionModel(respond),
        agent_id="1",
        message="入台证申请编号是多少",
        locale="zh-CN",
        budget=RunBudget(usage_limits=UsageLimits(request_limit=4)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "这一轮没有查到知识库记录，不能给出具体编号、状态或数字。"
    assert outcome.citations is None
    assert "A123456" not in outcome.reply
    assert len(calls) == 2

    async def stream_fn(_messages: list[ModelMessage], _info: AgentInfo):
        yield "申请编号 A123456 状态待送件"

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(respond, stream_function=stream_fn),
            agent_id="1",
            message="入台证申请编号是多少",
            locale="zh-CN",
            budget=RunBudget(usage_limits=UsageLimits(request_limit=4)),
        )
    ]
    done = events[-1]
    assert done.type == AgentStreamEventType.DONE
    assert done.data["reply"] == outcome.reply
    assert "citations" not in done.data


async def test_fake_model_skips_retry_when_request_budget_is_spent() -> None:
    calls: list[str] = []

    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls.append("run")
        return ModelResponse(parts=[TextPart("申请编号 A123456")])

    outcome = await run_chat(
        FunctionModel(respond),
        agent_id="1",
        message="入台证编号",
        locale="en",
        budget=RunBudget(usage_limits=UsageLimits(request_limit=1)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply.startswith("No knowledge-base record")
    assert outcome.citations is None
    assert len(calls) == 1


async def test_fake_model_aggregate_number_absent_from_tool_text_is_replaced() -> None:
    calls: list[int] = []
    outcome = await run_chat(
        FunctionModel(_search_then("一共 120 小时", calls)),
        agent_id="1",
        message="10 月考勤统计",
        locale="zh-CN",
        toolsets=[build_knowledge_toolset("1", _StubRetrieval())],  # type: ignore[list-item]
        knowledge_collector=KnowledgeSearchCollector(),
        budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "片段检索无法给出全量统计。"
    assert "120" not in outcome.reply
    assert len(calls) >= 2


async def test_fake_model_appends_degraded_note_when_reply_omits_it() -> None:
    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen_tool = any(type(part).__name__ == "ToolReturnPart" for message in messages for part in message.parts)
        if not seen_tool:
            return ModelResponse(parts=[ToolCallPart(tool_name="search_site_knowledge", args={"query": "note"})])
        return ModelResponse(parts=[TextPart("the note is ready")])

    collector = KnowledgeSearchCollector()
    outcome = await run_chat(
        FunctionModel(respond),
        agent_id="1",
        message="hello",
        locale="ja-JP",
        toolsets=[build_knowledge_toolset("1", _StubRetrieval(degraded=True), collector=collector)],  # type: ignore[list-item]
        knowledge_collector=collector,
        budget=RunBudget(usage_limits=UsageLimits(request_limit=6, tool_calls_limit=6)),
    )
    assert isinstance(outcome, ChatReply)
    assert collector.degraded is True
    assert outcome.reply.startswith("the note is ready")
    assert outcome.reply.rstrip().endswith("reliability is lower.")


def test_golden_questions_are_deterministic() -> None:
    refusal = "这一轮没有查到知识库记录，不能给出具体编号、状态或数字。"
    total = "片段检索无法给出全量统计。"

    action, reply = plan_search_grounding(
        "入台证申请日期是哪天",
        "申请日期是 2024-03-01",
        KnowledgeSearchCollector(),
        "zh-CN",
        allow_retry=False,
    )
    assert action == "refuse"
    assert reply == refusal
    assert "2024" not in reply

    action, reply = plan_search_grounding(
        "入台证编号",
        "编号是 A123456",
        None,
        "zh-CN",
        allow_retry=False,
    )
    assert action == "refuse"
    assert "A123456" not in reply

    collector = KnowledgeSearchCollector()
    collector.record("考勤", [RetrievedChunk("kb:14", 0, "十月出勤记录", 0.9, title="10月考勤表")])
    action, reply = plan_search_grounding(
        "10 月考勤统计",
        "一共 120 小时",
        collector,
        "zh-CN",
        allow_retry=False,
    )
    assert action == "aggregate"
    assert reply == total
    assert "120" not in reply

    action, reply = plan_search_grounding(
        "能否读数据库",
        "我不能直接查询数据库，只能搜索知识库。",
        KnowledgeSearchCollector(),
        "zh-CN",
        allow_retry=False,
    )
    assert action == "keep"
    assert reply == "我不能直接查询数据库，只能搜索知识库。"
