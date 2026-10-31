"""主 agent 直接持有知识搜索：工具集组装、工具返回文案与提示词引导。"""

from conftest import make_settings

from agent_services.agent.harness import assemble_harness
from agent_services.agent.knowledge_tools import (
    KnowledgeSearchCollector,
    build_knowledge_toolset,
)
from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.retrieval.store import RetrievedChunk

_GUIDANCE_MARKER = "search_site_knowledge tool first"


class _FakeRetrieval:
    def __init__(self, *, degraded: bool = False, fail: bool = False, partial: bool = False) -> None:
        self.calls: list[tuple[str, str]] = []
        self.kwargs: list[dict[str, object]] = []
        self.degraded = degraded
        self._fail = fail
        self._partial = partial

    async def retrieve_chunks(self, *, agent_id: str, query: str, **kwargs: object) -> list[RetrievedChunk]:
        self.calls.append((agent_id, query))
        self.kwargs.append(dict(kwargs))
        if self._fail:
            raise RuntimeError("boom")
        if self.degraded and not self._partial:
            return []
        return [RetrievedChunk(source_id="kb:1", chunk_index=2, content="KB-FACT", score=0.9)]


def _tool_names(toolsets: list[object]) -> set[str]:
    names: set[str] = set()
    for toolset in toolsets:
        tools = getattr(toolset, "tools", None)
        if not isinstance(tools, dict):
            continue
        for key, tool in tools.items():
            raw = getattr(tool, "name", None) or key
            if isinstance(raw, str):
                names.add(raw)
    return names


def test_execution_toolsets_include_knowledge_search_when_retrieval_present() -> None:
    toolsets = build_execution_toolsets(
        make_settings(),
        "42",
        todo_store=TodoStore(),
        retrieval=_FakeRetrieval(),  # type: ignore[arg-type]
    )
    assert "search_site_knowledge" in _tool_names(toolsets)


def test_execution_toolsets_omit_knowledge_search_without_retrieval() -> None:
    toolsets = build_execution_toolsets(
        make_settings(),
        "42",
        todo_store=TodoStore(),
        retrieval=None,
    )
    assert "search_site_knowledge" not in _tool_names(toolsets)


async def _call_search(toolset: object, query: str) -> str:
    tool = toolset.tools["search_site_knowledge"]  # type: ignore[attr-defined]
    return await tool.function(query=query)


async def test_knowledge_tool_returns_formatted_chunks_with_sources() -> None:
    retrieval = _FakeRetrieval()
    toolset = build_knowledge_toolset("42", retrieval)  # type: ignore[arg-type]
    result = await _call_search(toolset, "hours")
    assert result == "- [c1] KB-FACT (source: kb:1#2)"
    assert retrieval.calls == [("42", "hours")]


async def test_knowledge_tool_reports_unavailable_without_retrieval() -> None:
    toolset = build_knowledge_toolset("42", None)
    assert await _call_search(toolset, "hours") == "Site knowledge search is unavailable."


async def test_knowledge_tool_returns_partial_hits_when_degraded() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset(
        "42",
        _FakeRetrieval(degraded=True, partial=True),  # type: ignore[arg-type]
        collector=collector,
    )
    assert await _call_search(toolset, "hours") == (
        "Search degraded: at least one embedding class failed. "
        "The passages below are incomplete.\n\n"
        "- [c1] KB-FACT (source: kb:1#2)"
    )
    assert collector.degraded is True
    assert [(chunk.source_id, chunk.chunk_index) for chunk in collector.chunks] == [("kb:1", 2)]


async def test_knowledge_tool_reports_failure_when_degraded() -> None:
    toolset = build_knowledge_toolset("42", _FakeRetrieval(degraded=True))  # type: ignore[arg-type]
    assert await _call_search(toolset, "hours") == "Site knowledge search failed."


async def test_knowledge_tool_reports_failure_when_retrieval_raises() -> None:
    toolset = build_knowledge_toolset("42", _FakeRetrieval(fail=True))  # type: ignore[arg-type]
    assert await _call_search(toolset, "hours") == "Site knowledge search failed."


def test_prompt_parts_mention_knowledge_tool_only_when_available() -> None:
    with_tool, _ = build_prompt_parts("1", "hi", knowledge_available=True)
    without_tool, _ = build_prompt_parts("1", "hi")
    assert "search_site_knowledge" in with_tool
    assert "documents or facts" in with_tool
    # 站点专属值优先于通识：看似通识的问题也必须先查（防凭参数知识绕过工具）
    assert "takes precedence over general knowledge" in with_tool
    # 复合问题逐点分开检索（单条混合查询的向量会稀释各分话题，真嵌入探针实测）
    assert "separate search for each part" in with_tool
    assert "search_site_knowledge" not in without_tool


async def test_knowledge_tool_records_hits_in_collector() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("42", _FakeRetrieval(), collector=collector)  # type: ignore[arg-type]
    await _call_search(toolset, "hours")
    await _call_search(toolset, "hours again")
    # 同一 (source, chunk) 去重；逐次摘要保留每次调用的命中情况。
    assert [(c.source_id, c.chunk_index) for c in collector.chunks] == [("kb:1", 2)]
    assert collector.degraded is False
    first = collector.summary_for(1)
    assert first is not None
    assert first["query"] == "hours"
    assert first["hitCount"] == 1
    assert collector.summary_for(3) is None


async def test_knowledge_tool_marks_collector_degraded_on_failure() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("42", _FakeRetrieval(fail=True), collector=collector)  # type: ignore[arg-type]
    assert await _call_search(toolset, "hours") == "Site knowledge search failed."
    assert collector.degraded is True
    assert collector.chunks == []


async def test_knowledge_tool_passes_request_embedder_and_selection() -> None:
    retrieval = _FakeRetrieval()
    embedder = object()
    selection = object()
    toolset = build_knowledge_toolset(
        "42",
        retrieval,  # type: ignore[arg-type]
        embedder=embedder,  # type: ignore[arg-type]
        model_selection=selection,  # type: ignore[arg-type]
    )
    await _call_search(toolset, "hours")
    assert retrieval.kwargs[0]["embedder"] is embedder
    assert retrieval.kwargs[0]["model_selection"] is selection


def test_harness_adds_knowledge_guidance_only_when_tool_registered(tmp_path) -> None:
    with_tool = assemble_harness(
        agent_id="42",
        message="hi",
        toolsets=[build_knowledge_toolset("42", _FakeRetrieval())],  # type: ignore[arg-type]
        harness_dir=str(tmp_path),
    )
    without_tool = assemble_harness(
        agent_id="42",
        message="hi",
        toolsets=[],
        harness_dir=str(tmp_path),
    )
    assert _GUIDANCE_MARKER in with_tool.system_prompt
    assert "search_site_knowledge" not in without_tool.system_prompt
