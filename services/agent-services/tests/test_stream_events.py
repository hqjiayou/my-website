"""流式事件映射：THINKING / TOOL_START / TOOL_RESULT / DONE 引用与降级。

用 FunctionModel 的 stream_function 驱动 run_stream_events：
第一轮产出思考片段并调 search_site_knowledge，第二轮据工具返回产出文本。
"""

from collections.abc import AsyncIterator

from pydantic_ai.messages import (
    ModelMessage,
    ToolReturnPart,
)
from pydantic_ai.models.function import (
    AgentInfo,
    DeltaThinkingPart,
    DeltaToolCall,
    FunctionModel,
)

from agent_services.agent.knowledge_tools import (
    KnowledgeSearchCollector,
    build_knowledge_toolset,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.stream import AgentStreamEvent


class _FakeRetrieval:
    degraded = False

    async def retrieve_chunks(self, *, site_id: str, query: str, **_kwargs: object) -> list[RetrievedChunk]:
        return [RetrievedChunk(source_id="kb:1", chunk_index=2, content="KB-FACT", score=0.9)]


def _has_tool_return(messages: list[ModelMessage]) -> bool:
    return any(
        isinstance(part, ToolReturnPart)
        for message in messages
        for part in getattr(message, "parts", [])
    )


async def _collect(model: FunctionModel, **kwargs: object) -> list[AgentStreamEvent]:
    return [
        event
        async for event in stream_chat_events(model, site_id="1", message="hours?", **kwargs)  # type: ignore[arg-type]
    ]


async def test_stream_maps_thinking_tool_and_citations() -> None:
    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        if _has_tool_return(messages):
            yield "KB-FACT answer"
            return
        yield {0: DeltaThinkingPart(content="check the knowledge base first")}
        yield {1: DeltaToolCall(name="search_site_knowledge", json_args='{"query": "hours"}')}


    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("1", _FakeRetrieval(), collector=collector)  # type: ignore[arg-type]
    events = await _collect(
        FunctionModel(stream_function=stream_fn),
        toolsets=[toolset],
        knowledge_collector=collector,
    )

    types = [event.type.value for event in events]
    assert types[0] == "START"
    assert types[-1] == "DONE"
    assert "THINKING" in types
    assert types.index("THINKING") < types.index("TOOL_START") < types.index("TOOL_RESULT")

    thinking = next(event for event in events if event.type.value == "THINKING")
    assert "knowledge base" in str(thinking.data["delta"])

    tool_result = next(event for event in events if event.type.value == "TOOL_RESULT")
    assert tool_result.data["toolName"] == "search_site_knowledge"
    assert tool_result.data["ok"] is True
    summary = tool_result.data["summary"]
    assert isinstance(summary, dict)
    assert summary["hitCount"] == 1
    assert summary["sources"][0]["sourceId"] == "kb:1"  # type: ignore[index]
    # 工具返回正文不进事件（§21.4）：摘要里不许出现片段内容
    assert "KB-FACT" not in str(summary)

    done = events[-1]
    assert done.data["reply"] == "KB-FACT answer"
    assert done.data["tools"] == [{"name": "search_site_knowledge", "ok": True}]
    assert done.data["retrievalDegraded"] is False
    citations = done.data["citations"]
    assert isinstance(citations, list)
    assert citations[0]["sourceId"] == "kb:1"  # type: ignore[index]
    # 搜索摘要随 DONE 输出，供 Java 落库后在历史里回放过程摘要
    searches = done.data["searches"]
    assert isinstance(searches, list)
    assert searches[0]["query"] == "hours"  # type: ignore[index]
    assert searches[0]["hitCount"] == 1  # type: ignore[index]


async def test_stream_without_thinking_parts_emits_no_thinking_event() -> None:
    async def stream_fn(_messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        yield "plain answer"

    events = await _collect(FunctionModel(stream_function=stream_fn))
    types = [event.type.value for event in events]
    assert "THINKING" not in types
    assert types[-1] == "DONE"
    assert events[-1].data["reply"] == "plain answer"
