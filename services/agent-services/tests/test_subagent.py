"""delegate_task：受限子 agent，失败不拖垮主 run，用量并入本轮。"""

import asyncio

from conftest import make_settings
from pydantic_ai.messages import (
    ModelMessage,
    ModelRequest,
    ModelResponse,
    TextPart,
    ToolCallPart,
    ToolReturnPart,
)
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel
from pydantic_ai.toolsets.function import FunctionToolset
from pydantic_ai.usage import UsageLimits

from agent_services.agent.harness import assemble_harness
from agent_services.agent.runtime import ChatReply, RunBudget, run_chat
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.agent.subagent import (
    DELEGATE_RESULT_MAX_CHARS,
    _limit_result,
    build_delegate_tool,
    build_research_agent,
)
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.agent.weather_tools import build_weather_toolset
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.stream import AgentStreamEventType


class _FakeRetrieval:
    def __init__(self, content: str = "RESEARCH-FACT") -> None:
        self.calls: list[tuple[list[str], str]] = []
        self.degraded = False
        self._content = content

    async def retrieve_chunks(self, *, base_ids: list[str], query: str, **_kwargs: object) -> list[RetrievedChunk]:
        self.calls.append((list(base_ids), query))
        return [RetrievedChunk(source_id="kb:1", chunk_index=2, content=self._content, score=0.9)]


def _returns(messages: list[ModelMessage], name: str) -> list[ToolReturnPart]:
    found: list[ToolReturnPart] = []
    for message in messages:
        if not isinstance(message, ModelRequest):
            continue
        for part in message.parts:
            if isinstance(part, ToolReturnPart) and part.tool_name == name:
                found.append(part)
    return found


def _is_research(info: AgentInfo) -> bool:
    names = {tool.name for tool in info.function_tools}
    return "search_site_knowledge" in names and "delegate_task" not in names


def _tool_names(agent: object) -> set[str]:
    names: set[str] = set()
    for toolset in getattr(agent, "toolsets", []):
        tools = getattr(toolset, "tools", None)
        if not isinstance(tools, dict):
            continue
        for key, tool in tools.items():
            raw = getattr(tool, "name", None) or key
            if isinstance(raw, str):
                names.add(raw)
    return names


def _budget() -> RunBudget:
    return RunBudget(usage_limits=UsageLimits(request_limit=30, total_tokens_limit=100_000))


async def test_delegate_returns_research_conclusion_to_the_parent() -> None:
    retrieval = _FakeRetrieval()
    seen_tools: list[set[str]] = []

    def respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen_tools.append({tool.name for tool in info.function_tools})
        if _is_research(info):
            if not _returns(messages, "search_site_knowledge"):
                return ModelResponse(
                    parts=[ToolCallPart(tool_name="search_site_knowledge", args={"query": "hours"})]
                )
            return ModelResponse(parts=[TextPart("conclusion RESEARCH-FACT")])
        if not _returns(messages, "delegate_task"):
            return ModelResponse(
                parts=[ToolCallPart(tool_name="delegate_task", args={"task": "look up hours"})]
            )
        return ModelResponse(parts=[TextPart("answer RESEARCH-FACT")])

    model = FunctionModel(respond)
    toolset = build_delegate_tool(model, agent_id="42", retrieval=retrieval, knowledge_base_ids=["42"])  # type: ignore[arg-type]
    outcome = await run_chat(
        model,
        agent_id="42",
        message="what are the hours?",
        toolsets=[toolset],
        budget=_budget(),
    )
    assert isinstance(outcome, ChatReply)
    assert "RESEARCH-FACT" in outcome.reply
    assert outcome.tools == [{"name": "delegate_task", "ok": True}]
    assert retrieval.calls == [(["42"], "hours")]
    assert {"search_site_knowledge"} in seen_tools
    assert all("delegate_task" not in names or "search_site_knowledge" not in names for names in seen_tools)


def test_research_agent_exposes_only_knowledge_search() -> None:
    agent = build_research_agent(FunctionModel(lambda _m, _i: ModelResponse(parts=[TextPart("x")])), agent_id="1", retrieval=None)
    assert _tool_names(agent) == {"search_site_knowledge"}
    assert "delegate_task" not in _tool_names(agent)
    joined = " ".join(_tool_names(agent))
    for business in ("get_site_info", "list_site_contents", "list_todos"):
        assert business not in joined


async def test_fifth_delegation_returns_limit_and_parent_finishes() -> None:
    seen: list[str] = []

    def respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        if _is_research(info):
            return ModelResponse(parts=[TextPart("noted")])
        done = _returns(messages, "delegate_task")
        if len(done) < 5:
            return ModelResponse(
                parts=[ToolCallPart(tool_name="delegate_task", args={"task": f"part {len(done)}"})]
            )
        return ModelResponse(parts=[TextPart("finished")])

    model = FunctionModel(respond)
    toolset = build_delegate_tool(model, agent_id="1", retrieval=None)
    outcome = await run_chat(model, agent_id="1", message="split this", toolsets=[toolset], budget=_budget())
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "finished"
    assert [item["ok"] for item in outcome.tools] == [True, True, True, True, False]
    assert outcome.tools[-1] == {"name": "delegate_task", "ok": False}
    assert any("at most 4 per run" in blob for blob in seen)


async def test_slow_subagent_returns_timeout_string_and_parent_finishes() -> None:
    seen: list[str] = []

    async def respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        if _is_research(info):
            await asyncio.sleep(2)
            return ModelResponse(parts=[TextPart("too late")])
        if not _returns(messages, "delegate_task"):
            return ModelResponse(parts=[ToolCallPart(tool_name="delegate_task", args={"task": "slow lookup"})])
        return ModelResponse(parts=[TextPart("parent-done")])

    model = FunctionModel(respond)
    toolset = build_delegate_tool(model, agent_id="1", retrieval=None, timeout_seconds=0.2)
    outcome = await run_chat(
        model,
        agent_id="1",
        message="go",
        toolsets=[toolset],
        budget=RunBudget(
            usage_limits=UsageLimits(request_limit=10),
            tool_timeout_seconds=0.05,
        ),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "parent-done"
    assert any("Delegation timed out after 0.2 seconds." in blob for blob in seen)
    assert outcome.tools == [{"name": "delegate_task", "ok": False}]


async def test_subagent_usage_is_added_above_the_parent_only_baseline() -> None:
    def delegated(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        if _is_research(info):
            return ModelResponse(parts=[TextPart("SUB-FACT")])
        if not _returns(messages, "delegate_task"):
            return ModelResponse(parts=[ToolCallPart(tool_name="delegate_task", args={"task": "find SUB-FACT"})])
        return ModelResponse(parts=[TextPart("answer SUB-FACT")])

    def baseline(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        if not _returns(messages, "ping"):
            return ModelResponse(parts=[ToolCallPart(tool_name="ping", args={})])
        return ModelResponse(parts=[TextPart("answer")])

    baseline_tools = FunctionToolset()

    @baseline_tools.tool_plain
    async def ping() -> str:
        """Ping."""
        return "pong"

    budget = _budget()
    parent = FunctionModel(delegated)
    delegated_outcome = await run_chat(
        parent,
        agent_id="1",
        message="go",
        toolsets=[build_delegate_tool(parent, agent_id="1", retrieval=None)],
        budget=budget,
    )
    baseline_outcome = await run_chat(
        FunctionModel(baseline),
        agent_id="1",
        message="go",
        toolsets=[baseline_tools],
        budget=budget,
    )
    assert isinstance(delegated_outcome, ChatReply)
    assert isinstance(baseline_outcome, ChatReply)
    assert "SUB-FACT" in delegated_outcome.reply
    assert delegated_outcome.usage["requests"] == baseline_outcome.usage["requests"] + 1
    delegated_tokens = delegated_outcome.usage["inputTokens"] + delegated_outcome.usage["outputTokens"]
    baseline_tokens = baseline_outcome.usage["inputTokens"] + baseline_outcome.usage["outputTokens"]
    assert delegated_tokens > baseline_tokens


def test_disabled_omits_tool_and_leaves_harness_text_unchanged(tmp_path) -> None:
    (tmp_path / "background.md").write_text("BG", encoding="utf-8")
    model = FunctionModel(lambda _m, _i: ModelResponse(parts=[TextPart("x")]))
    common = {
        "agent_enable_todo_tools": True,
        "business_api_base_url": "",
        "mcp_servers_json": "",
    }
    disabled = build_execution_toolsets(
        make_settings(agent_subagent_enabled=False, **common),
        "9",
        todo_store=TodoStore(),
        model=model,
    )
    enabled = build_execution_toolsets(
        make_settings(agent_subagent_enabled=True, **common),
        "9",
        todo_store=TodoStore(),
        model=model,
    )
    assert all("delegate_task" not in _tool_names_from_toolset(toolset) for toolset in disabled)
    assert any("delegate_task" in _tool_names_from_toolset(toolset) for toolset in enabled)

    off = assemble_harness(agent_id="9", message="hi", toolsets=disabled, harness_dir=str(tmp_path))
    legacy = assemble_harness(
        agent_id="9",
        message="hi",
        # 基线＝子 agent 引入前的常开工具集；天气自 #164 起无条件注册，须一并列入，
        # 顺序与 build_execution_toolsets 一致（weather 在 todo 之前）。
        toolsets=[build_weather_toolset(None, None), build_todo_toolset(TodoStore())],
        harness_dir=str(tmp_path),
    )
    on = assemble_harness(agent_id="9", message="hi", toolsets=enabled, harness_dir=str(tmp_path))
    assert off.system_prompt == legacy.system_prompt
    assert "# Delegation" not in off.system_prompt
    assert "delegate_task" not in off.system_prompt
    assert "# Delegation" in on.system_prompt
    assert "self-contained" in on.system_prompt
    assert "in parallel" in on.system_prompt
    assert "business action" in on.system_prompt


async def test_stream_run_can_delegate() -> None:
    async def respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        if _is_research(info):
            return ModelResponse(parts=[TextPart("streamed RESEARCH-FACT")])
        if not _returns(messages, "delegate_task"):
            return ModelResponse(parts=[ToolCallPart(tool_name="delegate_task", args={"task": "find the fact"})])
        return ModelResponse(parts=[TextPart("final streamed RESEARCH-FACT")])

    async def stream_fn(messages: list[ModelMessage], info: AgentInfo):
        response = await respond(messages, info)
        part = response.parts[0]
        if isinstance(part, ToolCallPart):
            yield {0: DeltaToolCall(name=part.tool_name, json_args=part.args_as_json_str())}
            return
        assert isinstance(part, TextPart)
        yield part.content

    model = FunctionModel(respond, stream_function=stream_fn)
    events = [
        event
        async for event in stream_chat_events(
            model,
            agent_id="1",
            message="go",
            toolsets=[build_delegate_tool(model, agent_id="1", retrieval=None)],
            budget=_budget(),
        )
    ]
    done = next(event for event in events if event.type == AgentStreamEventType.DONE)
    assert "RESEARCH-FACT" in str(done.data["reply"])
    assert done.data["tools"] == [{"name": "delegate_task", "ok": True}]
    assert done.data["usage"]["requests"] >= 3


def _tool_names_from_toolset(toolset: object) -> set[str]:
    tools = getattr(toolset, "tools", None)
    if not isinstance(tools, dict):
        return set()
    names: set[str] = set()
    for key, tool in tools.items():
        raw = getattr(tool, "name", None) or key
        if isinstance(raw, str):
            names.add(raw)
    return names


def test_limit_result_never_exceeds_max_chars() -> None:
    assert _limit_result("short") == "short"
    limited = _limit_result("x" * (DELEGATE_RESULT_MAX_CHARS + 500))
    assert len(limited) == DELEGATE_RESULT_MAX_CHARS
    assert limited.endswith("[truncated]")
