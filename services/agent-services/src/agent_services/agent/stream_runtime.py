"""流式 chat 执行：Pydantic AI stream → 应用层 AgentStreamEvent。"""

import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass, field

from pydantic_ai import Agent
from pydantic_ai.exceptions import UsageLimitExceeded
from pydantic_ai.messages import (
    FunctionToolCallEvent,
    FunctionToolResultEvent,
    PartDeltaEvent,
    PartStartEvent,
    TextPartDelta,
    ThinkingPart,
    ThinkingPartDelta,
)
from pydantic_ai.models import Model
from pydantic_ai.run import AgentRunResultEvent

from agent_services.agent.harness import assemble_harness
from agent_services.agent.knowledge_tools import (
    SEARCH_TOOL_NAME,
    KnowledgeSearchCollector,
)
from agent_services.agent.memory import extract_memories
from agent_services.agent.runtime import (
    BUDGET_EXCEEDED,
    MAX_TOOL_TRACE,
    RunBudget,
    UserPrompt,
    _apply_search_grounding,
    _user_prompt_texts,
    budget_exceeded_message,
    build_agent,
    build_run_kwargs,
    build_user_prompt,
    mcp_unavailable_message,
    model_identity_line,
    prepare_history_summary,
    reply_citations,
    tool_content_text,
    tool_succeeded,
    usage_to_api,
)
from agent_services.agent.skill_tools import SkillLoadCollector, merge_skills_applied
from agent_services.agent.skills import SkillDefinition
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import execution_needs_agent_context
from agent_services.agent.visualization import VisualizationCollector
from agent_services.api.errors import external_failure_message
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import (
    ChatAttachment,
    ChatDocument,
    ChatHistoryMessage,
    ExperienceLessonItem,
    PromptProfile,
)
from agent_services.schemas.stream import AgentStreamEvent, AgentStreamEventType
from agent_services.schemas.todo import TodoItem

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class _StreamOutput:
    reply: str
    usage: dict[str, int]
    tools: list[dict[str, object]]
    model: str | None = None
    # 本轮工具返回文本，仅供 grounding 的 R3 统一语料（Issue #191），不进事件流。
    tool_texts: list[str] = field(default_factory=list)


def _error_event(error_code: str, message: str) -> AgentStreamEvent:
    return AgentStreamEvent(
        type=AgentStreamEventType.ERROR,
        data={"errorCode": error_code, "message": message},
    )


async def stream_chat_events(
    model: Model,
    *,
    agent_id: str,
    message: str,
    attachments: list[ChatAttachment] | None = None,
    documents: list[ChatDocument] | None = None,
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
    timezone_name: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
    accuracy_meters: float | None = None,
    skills_max_instruction_chars: int | None = None,
    skill_ids: list[str] | None = None,
    harness_dir: str | None = None,
    memories: list[str] | None = None,
    memory_enabled: bool = False,
    memory_extraction_enabled: bool = True,
    experience_lessons: list[ExperienceLessonItem] | None = None,
    experience_lessons_enabled: bool = True,
    knowledge_collector: KnowledgeSearchCollector | None = None,
    visualization_collector: VisualizationCollector | None = None,
    skill_catalog: list[SkillDefinition] | None = None,
    skills_self_select_enabled: bool = False,
    skill_catalog_exclude_ids: tuple[str, ...] = (),
    skill_load_collector: SkillLoadCollector | None = None,
) -> AsyncIterator[AgentStreamEvent]:
    resolved_budget = budget or RunBudget()
    history_summary = await prepare_history_summary(
        model, history, resolved_budget.history_max_chars
    )
    harness = assemble_harness(
        agent_id=agent_id,
        message=message,
        prompt_profile=prompt_profile,
        retrieval_context=retrieval_context,
        skill_instructions=skill_instructions,
        skill_ids=skill_ids if skill_ids is not None else skills_applied,
        skills_missing=skills_missing,
        history=history,
        todos=initial_todos,
        locale=locale,
        timezone_name=timezone_name,
        latitude=latitude,
        longitude=longitude,
        accuracy_meters=accuracy_meters,
        skills_max_instruction_chars=skills_max_instruction_chars,
        toolsets=toolsets,
        budget=resolved_budget,
        harness_dir=harness_dir,
        history_summary=history_summary,
        memories=memories,
        experience_lessons=experience_lessons,
        experience_lessons_enabled=experience_lessons_enabled,
        skill_catalog=skill_catalog,
        skills_self_select_enabled=skills_self_select_enabled,
        skill_catalog_exclude_ids=skill_catalog_exclude_ids,
    )
    system_prompt = harness.system_prompt
    user_prompt = build_user_prompt(harness.user_message, attachments, documents)
    start_data: dict[str, object] = {"agentId": agent_id}
    if skills_applied:
        start_data["skillsApplied"] = list(skills_applied)
    if skills_missing:
        start_data["skillsMissing"] = list(skills_missing)
    yield AgentStreamEvent(type=AgentStreamEventType.START, data=start_data)

    run_kwargs = build_run_kwargs(prompt_profile, list(harness.memory), resolved_budget)
    identity = model_identity_line(model)
    agent = build_agent(
        model,
        f"{system_prompt}\n\n{identity}" if identity else system_prompt,
        toolsets,
        resolved_budget,
    )

    output: _StreamOutput | None = None
    try:
        if execution_needs_agent_context(toolsets or []):
            entered = False
            try:
                async with agent:
                    entered = True
                    async for event in _emit_agent_stream(
                        agent,
                        user_prompt,
                        run_kwargs,
                        knowledge_collector,
                        visualization_collector,
                    ):
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
            async for event in _emit_agent_stream(
                agent, user_prompt, run_kwargs, knowledge_collector, visualization_collector
            ):
                if isinstance(event, AgentStreamEvent):
                    yield event
                else:
                    output = event
    except UsageLimitExceeded as exc:
        # 已流出的部分文本不作为成功回复：没有 DONE，Java 不落库
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

    identity = model_identity_line(model)
    base_prompt = f"{system_prompt}\n\n{identity}" if identity else system_prompt
    grounded_reply, clear_citations, grounded_usage, grounded_tools = await _apply_search_grounding(
        model=model,
        message=message,
        reply=output.reply,
        locale=locale,
        collector=knowledge_collector,
        budget=resolved_budget,
        usage=output.usage,
        tools=list(output.tools),
        base_prompt=base_prompt,
        user_prompt=user_prompt,
        toolsets=toolsets,
        prompt_profile=prompt_profile,
        history=list(harness.memory),
        needs_mcp_context=execution_needs_agent_context(toolsets or []),
        corpus_texts=[*output.tool_texts, *_user_prompt_texts(user_prompt)],
        platform_texts=harness.platform_texts,
        retrieval_context=retrieval_context,
        retrieval_chunks=retrieval_chunks,
    )

    # grounding 复跑在收尾阶段也可能产卡（同一批 toolsets）：DONE 前把残余卡片
    # 按序补发，不让已记录的卡片丢失。主 run 的卡片已在 TOOL_RESULT 后逐个发出。
    if visualization_collector is not None:
        for card in visualization_collector.drain():
            yield AgentStreamEvent(type=AgentStreamEventType.VISUALIZATION, data=card)

    # 与同步 chat 一样恒带 retrievalDegraded：预取降级或知识搜索工具降级任一成立即为真
    tool_degraded = knowledge_collector.degraded if knowledge_collector is not None else False
    done_data: dict[str, object] = {
        "reply": grounded_reply,
        "providerStatus": "LIVE",
        "usage": grounded_usage,
        "tools": grounded_tools,
        "retrievalDegraded": retrieval_degraded or tool_degraded,
    }
    if output.model:
        done_data["model"] = output.model
    # DONE 的 skillsApplied 是本轮最终集：预选在前，run 中经 load_skill 自选
    # 加载的随后合并（Issue #195）。START 事件在 run 开始前发出、只能报预选集，
    # 不回填——两者的时序语义不同，不追求一致。
    final_skills_applied = merge_skills_applied(
        skills_applied,
        skill_load_collector.loaded_ids if skill_load_collector is not None else None,
    )
    if final_skills_applied:
        done_data["skillsApplied"] = final_skills_applied
    if skills_missing:
        done_data["skillsMissing"] = list(skills_missing)
    if todo_store is not None:
        snapshot = todo_store.snapshot()
        if snapshot.items:
            done_data["todos"] = snapshot.to_api()
    citations = None
    if not clear_citations:
        citations = reply_citations(
            retrieval_context,
            retrieval_chunks,
            knowledge_collector.chunks if knowledge_collector is not None else None,
        )
    if citations:
        done_data["citations"] = citations
    # 搜索摘要随 DONE 落库（Java agentMeta），供历史回放显示过程摘要。
    if knowledge_collector is not None and knowledge_collector.searches:
        done_data["searches"] = list(knowledge_collector.searches)
    # 独立 Agent，不传入本轮 UsageLimits，抽取不计入 run budget。
    if memory_enabled and memory_extraction_enabled:
        extracted = await extract_memories(
            model,
            user_message=message,
            assistant_reply=grounded_reply,
            existing=memories,
        )
        if extracted:
            done_data["extractedMemories"] = extracted
    yield AgentStreamEvent(type=AgentStreamEventType.DONE, data=done_data)


async def _emit_agent_stream(
    agent: Agent,
    user_prompt: UserPrompt,
    run_kwargs: dict[str, object],
    knowledge_collector: KnowledgeSearchCollector | None,
    visualization_collector: VisualizationCollector | None = None,
) -> AsyncIterator[AgentStreamEvent | _StreamOutput]:
    """把 Pydantic AI 的 run 事件流映射为应用层事件（单一通道、真实时序）。

    文本增量 → TEXT_DELTA；思考增量 → THINKING（provider 不产出思考片段时
    自然缺席，不伪造）；工具调用/结果 → TOOL_START / TOOL_RESULT，结果只带
    执行标记与知识搜索的安全摘要，不带工具返回正文（§21.4）；工具在本轮
    产出的卡片在该工具的 TOOL_RESULT 之后紧接 VISUALIZATION 发出（卡片是
    给用户看的呈现，不算工具返回正文）。
    """
    text_parts: list[str] = []
    tool_trace: list[dict[str, object]] = []
    tool_texts: list[str] = []
    search_calls = 0
    final_output: str | None = None
    final_usage: dict[str, int] = {}
    final_model: str | None = None
    async with agent.run_stream_events(user_prompt, **run_kwargs) as events:
        async for event in events:
            if isinstance(event, PartStartEvent):
                # 部分 provider 在 part 起始就带首段思考内容；增量型 provider
                # 起始为空、内容全在 delta 里，两边都接才不丢字。
                if isinstance(event.part, ThinkingPart) and event.part.content:
                    yield AgentStreamEvent(
                        type=AgentStreamEventType.THINKING,
                        data={"delta": event.part.content},
                    )
            elif isinstance(event, PartDeltaEvent):
                delta = event.delta
                if isinstance(delta, TextPartDelta) and delta.content_delta:
                    text_parts.append(delta.content_delta)
                    yield AgentStreamEvent(
                        type=AgentStreamEventType.TEXT_DELTA,
                        data={"text": delta.content_delta, "delta": delta.content_delta},
                    )
                elif isinstance(delta, ThinkingPartDelta) and delta.content_delta:
                    yield AgentStreamEvent(
                        type=AgentStreamEventType.THINKING,
                        data={"delta": delta.content_delta},
                    )
            elif isinstance(event, FunctionToolCallEvent):
                yield AgentStreamEvent(
                    type=AgentStreamEventType.TOOL_START,
                    data={
                        "toolName": event.part.tool_name,
                        "toolCallId": event.tool_call_id,
                    },
                )
            elif isinstance(event, FunctionToolResultEvent):
                ok = tool_succeeded(event.part)
                if len(tool_trace) < MAX_TOOL_TRACE:
                    tool_trace.append({"name": event.part.tool_name, "ok": ok})
                # 返回正文只收进 grounding 语料，不进事件流（§21.4 不变）。
                content_text = tool_content_text(event.part.content)
                if content_text:
                    tool_texts.append(content_text)
                data: dict[str, object] = {
                    "toolName": event.part.tool_name,
                    "toolCallId": event.tool_call_id,
                    "ok": ok,
                }
                if event.part.tool_name == SEARCH_TOOL_NAME and knowledge_collector is not None:
                    search_calls += 1
                    summary = knowledge_collector.summary_for(search_calls)
                    if summary is not None:
                        data["summary"] = summary
                yield AgentStreamEvent(type=AgentStreamEventType.TOOL_RESULT, data=data)
                if visualization_collector is not None:
                    for card in visualization_collector.drain():
                        yield AgentStreamEvent(
                            type=AgentStreamEventType.VISUALIZATION, data=card
                        )
            elif isinstance(event, AgentRunResultEvent):
                result = event.result
                final_output = str(result.output).strip() if result.output is not None else ""
                final_usage = usage_to_api(result.usage)
                served = getattr(getattr(result, "response", None), "model_name", None)
                if isinstance(served, str) and served.strip():
                    final_model = served.strip()
    reply = final_output if final_output is not None else "".join(text_parts).strip()
    yield _StreamOutput(
        reply=reply, usage=final_usage, tools=tool_trace, model=final_model, tool_texts=tool_texts
    )
