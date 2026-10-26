"""流式 chat 执行：Pydantic AI stream → 应用层 AgentStreamEvent。"""

import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass

from pydantic_ai import Agent
from pydantic_ai.exceptions import UsageLimitExceeded
from pydantic_ai.messages import FunctionToolCallEvent, FunctionToolResultEvent
from pydantic_ai.models import Model

from agent_services.agent.harness import assemble_harness
from agent_services.agent.memory import extract_memories
from agent_services.agent.runtime import (
    BUDGET_EXCEEDED,
    MAX_TOOL_TRACE,
    RunBudget,
    UserPrompt,
    budget_exceeded_message,
    build_agent,
    build_run_kwargs,
    build_user_prompt,
    grounding_citations,
    mcp_unavailable_message,
    prepare_history_summary,
    tool_succeeded,
    usage_to_api,
)
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import execution_needs_agent_context
from agent_services.api.errors import external_failure_message
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import (
    ChatAttachment,
    ChatHistoryMessage,
    PromptProfile,
)
from agent_services.schemas.stream import AgentStreamEvent, AgentStreamEventType
from agent_services.schemas.todo import TodoItem

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class _StreamOutput:
    reply: str
    usage: dict[str, int]


def _error_event(error_code: str, message: str) -> AgentStreamEvent:
    return AgentStreamEvent(
        type=AgentStreamEventType.ERROR,
        data={"errorCode": error_code, "message": message},
    )


async def stream_chat_events(
    model: Model,
    *,
    site_id: str,
    message: str,
    attachments: list[ChatAttachment] | None = None,
    prompt_profile: PromptProfile | None = None,
    history: list[ChatHistoryMessage] | None = None,
    retrieval_context: list[str] | None = None,
    retrieval_chunks: list[RetrievedChunk] | None = None,
    retrieval_degraded: bool = False,
    skill_instructions: str | None = None,
    initial_todos: list[TodoItem] | None = None,
    toolsets: list[object] | None = None,
    todo_store: TodoStore | None = None,
    skills_applied: list[str] | None = None,
    skills_missing: list[str] | None = None,
    budget: RunBudget | None = None,
    locale: str | None = None,
    skills_max_instruction_chars: int | None = None,
    skill_ids: list[str] | None = None,
    harness_dir: str | None = None,
    memories: list[str] | None = None,
    memory_enabled: bool = False,
    memory_extraction_enabled: bool = True,
) -> AsyncIterator[AgentStreamEvent]:
    resolved_budget = budget or RunBudget()
    history_summary = await prepare_history_summary(
        model, history, resolved_budget.history_max_chars
    )
    harness = assemble_harness(
        site_id=site_id,
        message=message,
        prompt_profile=prompt_profile,
        retrieval_context=retrieval_context,
        skill_instructions=skill_instructions,
        skill_ids=skill_ids if skill_ids is not None else skills_applied,
        skills_missing=skills_missing,
        history=history,
        todos=initial_todos,
        locale=locale,
        skills_max_instruction_chars=skills_max_instruction_chars,
        toolsets=toolsets,
        budget=resolved_budget,
        harness_dir=harness_dir,
        history_summary=history_summary,
        memories=memories,
    )
    system_prompt = harness.system_prompt
    user_prompt = build_user_prompt(harness.user_message, attachments)
    start_data: dict[str, object] = {"siteId": site_id}
    if skills_applied:
        start_data["skillsApplied"] = list(skills_applied)
    if skills_missing:
        start_data["skillsMissing"] = list(skills_missing)
    yield AgentStreamEvent(type=AgentStreamEventType.START, data=start_data)

    run_kwargs = build_run_kwargs(prompt_profile, list(harness.memory), resolved_budget)
    side_events: list[AgentStreamEvent] = []
    tool_trace: list[dict[str, object]] = []

    async def map_tool_events(_ctx: object, events: object) -> None:
        async for event in events:  # type: ignore[union-attr]
            if isinstance(event, FunctionToolCallEvent):
                side_events.append(
                    AgentStreamEvent(
                        type=AgentStreamEventType.TOOL_START,
                        data={
                            "toolName": event.part.tool_name,
                            "toolCallId": event.tool_call_id,
                        },
                    )
                )
            elif isinstance(event, FunctionToolResultEvent):
                ok = tool_succeeded(event.part)
                if len(tool_trace) < MAX_TOOL_TRACE:
                    tool_trace.append({"name": event.part.tool_name, "ok": ok})
                # 只回执行结果标记，不回工具返回内容（防泄露 / 大 payload，§21.4）
                side_events.append(
                    AgentStreamEvent(
                        type=AgentStreamEventType.TOOL_RESULT,
                        data={
                            "toolName": event.part.tool_name,
                            "toolCallId": event.tool_call_id,
                            "ok": ok,
                        },
                    )
                )

    run_kwargs["event_stream_handler"] = map_tool_events
    agent = build_agent(model, system_prompt, toolsets, resolved_budget)

    output: _StreamOutput | None = None
    try:
        if execution_needs_agent_context(toolsets or []):
            entered = False
            try:
                async with agent:
                    entered = True
                    async for event in _emit_agent_stream(agent, user_prompt, run_kwargs, side_events):
                        if isinstance(event, AgentStreamEvent):
                            yield event
                        else:
                            output = event
            except UsageLimitExceeded:
                raise
            except Exception as exc:  # noqa: BLE001
                if not entered:
                    yield _error_event("MCP_UNAVAILABLE", mcp_unavailable_message(exc))
                    return
                logger.warning("AI provider call failed after MCP connect", exc_info=exc)
                yield _error_event(
                    "EXTERNAL_DEPENDENCY_BLOCKED",
                    external_failure_message("AI provider call failed", exc),
                )
                return
        else:
            async for event in _emit_agent_stream(agent, user_prompt, run_kwargs, side_events):
                if isinstance(event, AgentStreamEvent):
                    yield event
                else:
                    output = event
    except UsageLimitExceeded as exc:
        # 已流出的部分文本不作为成功回复：没有 DONE，Java 不落库
        while side_events:
            yield side_events.pop(0)
        yield _error_event(BUDGET_EXCEEDED, budget_exceeded_message(exc))
        return
    except Exception as exc:  # noqa: BLE001
        logger.warning("AI provider call failed", exc_info=exc)
        yield _error_event(
            "EXTERNAL_DEPENDENCY_BLOCKED",
            external_failure_message("AI provider call failed", exc),
        )
        return

    if output is None or not output.reply:
        yield _error_event("EXTERNAL_DEPENDENCY_BLOCKED", "AI provider returned an empty response.")
        return

    # 与同步 chat 一样恒带 retrievalDegraded（false 表示本轮检索未降级）
    done_data: dict[str, object] = {
        "reply": output.reply,
        "providerStatus": "LIVE",
        "usage": output.usage,
        "tools": tool_trace,
        "retrievalDegraded": retrieval_degraded,
    }
    if skills_applied:
        done_data["skillsApplied"] = list(skills_applied)
    if skills_missing:
        done_data["skillsMissing"] = list(skills_missing)
    if todo_store is not None:
        snapshot = todo_store.snapshot()
        if snapshot.items:
            done_data["todos"] = snapshot.to_api()
    citations = grounding_citations(retrieval_context, retrieval_chunks)
    if citations:
        done_data["citations"] = citations
    # 独立 Agent，不传入本轮 UsageLimits，抽取不计入 run budget。
    if memory_enabled and memory_extraction_enabled:
        extracted = await extract_memories(
            model,
            user_message=message,
            assistant_reply=output.reply,
            existing=memories,
        )
        if extracted:
            done_data["extractedMemories"] = extracted
    yield AgentStreamEvent(type=AgentStreamEventType.DONE, data=done_data)


async def _emit_agent_stream(
    agent: Agent,
    user_prompt: UserPrompt,
    run_kwargs: dict[str, object],
    side_events: list[AgentStreamEvent],
) -> AsyncIterator[AgentStreamEvent | _StreamOutput]:
    async with agent.run_stream(user_prompt, **run_kwargs) as stream:
        async for chunk in stream.stream_text(delta=True):
            while side_events:
                yield side_events.pop(0)
            if chunk:
                yield AgentStreamEvent(
                    type=AgentStreamEventType.TEXT_DELTA,
                    data={"text": chunk, "delta": chunk},
                )
        while side_events:
            yield side_events.pop(0)
        reply = (await stream.get_output()).strip()
        yield _StreamOutput(reply=reply, usage=usage_to_api(stream.usage))
