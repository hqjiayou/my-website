"""Chat 执行：Pydantic AI Agent 运行（Phase 2 起支持 MULTI-TURN）。

Python 不保存会话状态（stateless between business requests）；
历史由 Java 会话真相源加载后随请求传入，转换为 Pydantic AI message_history。
运行预算（§22.4）：每轮 UsageLimits + 工具超时；超限诚实失败 AGENT_BUDGET_EXCEEDED。
"""

import base64
import logging
from dataclasses import dataclass, field

import anyio
from pydantic_ai import Agent, BinaryContent
from pydantic_ai.exceptions import ModelRetry, UsageLimitExceeded
from pydantic_ai.messages import (
    ModelMessage,
    ModelRequest,
    ModelResponse,
    RetryPromptPart,
    TextPart,
    ToolReturnPart,
    UserPromptPart,
)
from pydantic_ai.models import Model
from pydantic_ai.settings import ModelSettings
from pydantic_ai.toolsets.wrapper import WrapperToolset
from pydantic_ai.usage import RunUsage, UsageLimits

from agent_services.agent.harness import assemble_harness
from agent_services.agent.memory import extract_memories
from agent_services.agent.toolset_factory import execution_needs_agent_context
from agent_services.api.errors import external_failure_message
from agent_services.config import Settings
from agent_services.observability.request_id import current_request_id
from agent_services.retrieval.store import RetrievedChunk, citation_sources
from agent_services.schemas.chat import (
    ChatAttachment,
    ChatHistoryMessage,
    PromptProfile,
)
from agent_services.schemas.todo import TodoItem

BUDGET_EXCEEDED = "AGENT_BUDGET_EXCEEDED"
HISTORY_SUMMARY_MAX_CHARS = 1000

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class RunBudget:
    """单轮运行预算；None 表示该维度不限。"""

    usage_limits: UsageLimits | None = None
    tool_timeout_seconds: float | None = None
    history_max_chars: int | None = None


def run_budget_from_settings(settings: Settings) -> RunBudget:
    def positive_or_none(value: int) -> int | None:
        return value if value > 0 else None

    return RunBudget(
        usage_limits=UsageLimits(
            request_limit=positive_or_none(settings.agent_max_requests),
            tool_calls_limit=positive_or_none(settings.agent_max_tool_calls),
            total_tokens_limit=positive_or_none(settings.agent_max_total_tokens),
        ),
        tool_timeout_seconds=settings.agent_tool_timeout_seconds
        if settings.agent_tool_timeout_seconds > 0
        else None,
        history_max_chars=positive_or_none(settings.agent_history_max_chars),
    )


def trim_history(
    history: list[ChatHistoryMessage] | None, max_chars: int | None
) -> list[ChatHistoryMessage]:
    """按字符预算保留最近的历史（§22.5）。

    从最新往前累加，超出即丢弃更早项；随后丢弃开头的非 USER 项，
    保证历史以 USER 开始（部分 Provider 拒绝以 assistant 开头的对话）。
    """
    if not history:
        return []
    if max_chars is None:
        return list(history)
    kept: list[ChatHistoryMessage] = []
    used = 0
    for item in reversed(history):
        used += len(item.content)
        if used > max_chars:
            break
        kept.append(item)
    kept.reverse()
    while kept and kept[0].role != "USER":
        kept.pop(0)
    return kept


def dropped_history(
    history: list[ChatHistoryMessage] | None, max_chars: int | None
) -> list[ChatHistoryMessage]:
    """trim_history 丢掉的前缀。保留项始终是原列表的后缀。"""
    if not history:
        return []
    kept = trim_history(history, max_chars)
    return list(history[: len(history) - len(kept)])


async def summary_for_dropped(
    history: list[ChatHistoryMessage] | None,
    max_chars: int | None,
    summarizer,
) -> str | None:
    """没有被裁掉的前缀时不调用摘要。空摘要视为没有。"""
    dropped = dropped_history(history, max_chars)
    if not dropped:
        return None
    text = await summarizer(dropped)
    cleaned = "" if text is None else str(text).strip()
    return cleaned or None


async def summarize_dropped_history(model: Model, dropped: list[ChatHistoryMessage]) -> str:
    """用本轮同一个模型压缩被裁掉的前缀。不把入口或进程密钥写进提示。"""
    transcript = "\n".join(f"{item.role}: {item.content}" for item in dropped)
    agent = Agent(
        model,
        system_prompt=(
            "Summarize the earlier conversation in one short paragraph. "
            "Keep names, decisions, and facts. Do not invent anything."
        ),
    )
    result = await agent.run(transcript)
    return str(result.output).strip()[:HISTORY_SUMMARY_MAX_CHARS]


async def prepare_history_summary(
    model: Model,
    history: list[ChatHistoryMessage] | None,
    max_chars: int | None,
) -> str | None:
    async def summarize(dropped: list[ChatHistoryMessage]) -> str:
        return await summarize_dropped_history(model, dropped)

    try:
        return await summary_for_dropped(history, max_chars, summarize)
    except Exception:  # noqa: BLE001 — 摘要失败只放弃摘要，不阻断本轮对话
        logger.warning("history summary failed; continuing with trimmed history", exc_info=True)
        return None


def usage_to_api(usage: RunUsage) -> dict[str, int]:
    return {
        "requests": usage.requests,
        "toolCalls": usage.tool_calls,
        "inputTokens": usage.input_tokens,
        "outputTokens": usage.output_tokens,
    }


# 轨迹只记工具名与成败，不记参数/返回内容（防泄露，§21.4/§22.8）；条数上限防异常循环撑大持久化
MAX_TOOL_TRACE = 50


def tool_succeeded(part: ToolReturnPart | RetryPromptPart) -> bool:
    """框架层重试（参数校验失败 / MCP 工具报错 / 超时）或平台工具约定的 {"error": ...} 返回 → 失败。

    2.60 起工具超时与框架重试不再单独产出 RetryPromptPart，而是
    ToolReturnPart(outcome='retried', retry=...)；outcome / retry 防御式读取，
    2.54 对象没有这些字段时不抛错。
    """
    if isinstance(part, RetryPromptPart):
        return False
    # 任一成立即失败：outcome 标记 retried，或携带框架重试信息（2.60 为 RetryInfo）
    if getattr(part, "outcome", None) == "retried" or getattr(part, "retry", None) is not None:
        return False
    content = part.content
    return not (isinstance(content, dict) and "error" in content)


def tool_trace_from_messages(messages: list[ModelMessage]) -> list[dict[str, object]]:
    trace: list[dict[str, object]] = []
    for message in messages:
        if not isinstance(message, ModelRequest):
            continue
        for part in message.parts:
            # RetryPromptPart 无 tool_name 时是输出校验重试，不属于工具调用
            if isinstance(part, (ToolReturnPart, RetryPromptPart)) and part.tool_name:
                trace.append({"name": part.tool_name, "ok": tool_succeeded(part)})
    return trace[:MAX_TOOL_TRACE]


def grounding_citations(
    retrieval_context: list[str] | None,
    retrieval_chunks: list[RetrievedChunk] | None,
) -> list[dict[str, object]] | None:
    """本轮确实把 chunks 写进提示时才给出引用；降级或未检索时调用方应传入空上下文。"""
    if not retrieval_context or not retrieval_chunks:
        return None
    citations = citation_sources(retrieval_chunks)
    return citations or None


@dataclass(frozen=True)
class ChatReply:
    reply: str
    usage: dict[str, int] = field(default_factory=dict)
    tools: list[dict[str, object]] = field(default_factory=list)
    citations: list[dict[str, object]] | None = None
    extracted_memories: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class ChatBlocked:
    """本轮未产出回复（外部依赖失败 / MCP 不可用 / 预算超限），一律诚实失败。"""

    message: str
    error_code: str = "EXTERNAL_DEPENDENCY_BLOCKED"


ChatOutcome = ChatReply | ChatBlocked


def mcp_unavailable_message(exc: BaseException) -> str:
    """连接失败常把 MCP URL 放进异常正文；只回异常类型。"""
    return external_failure_message("MCP unavailable", exc)


def budget_exceeded_message(exc: BaseException) -> str:
    """保留限额说明；正文里出现 URL 或凭据形态时只回异常类型。"""
    detail = " ".join(str(exc).split())
    if not detail or "://" in detail or "@" in detail:
        return external_failure_message("Agent run budget exceeded", exc)
    return f"Agent run budget exceeded: {detail}"


UserPrompt = str | list[str | BinaryContent]


def build_user_prompt(text: str, attachments: list[ChatAttachment] | None) -> UserPrompt:
    """无附件时原样返回字符串。有图时为 [非空文字] + 每张图一个 BinaryContent。"""
    images = [
        BinaryContent(data=base64.b64decode(item.data), media_type=item.media_type)
        for item in attachments or []
    ]
    if not images:
        return text
    parts: list[str | BinaryContent] = []
    if text:
        parts.append(text)
    parts.extend(images)
    return parts


def build_message_history(history: list[ChatHistoryMessage] | None) -> list[ModelMessage]:
    """Java 传入的扁平历史 → Pydantic AI ModelMessage；顺序保持原样。"""
    if not history:
        return []
    messages: list[ModelMessage] = []
    for item in history:
        if item.role == "USER":
            messages.append(
                ModelRequest(parts=[UserPromptPart(content=build_user_prompt(item.content, item.attachments))])
            )
        else:
            messages.append(ModelResponse(parts=[TextPart(content=item.content)]))
    return messages


@dataclass
class _TimeoutToolset(WrapperToolset):
    """单次工具调用超时 → ModelRetry（模型收到重试提示，TOOL_RESULT.ok=false）。

    Agent(tool_timeout=...) 与 FunctionToolset(timeout=...) 只作用于函数工具，
    不覆盖 MCPToolset（2.54 仍只有连接级 init_timeout / read_timeout），故在此统一包装。
    同步工具在线程中执行，超时无法中断线程本身。
    """

    timeout_seconds: float = 30.0

    async def call_tool(self, name, tool_args, ctx, tool):  # type: ignore[no-untyped-def]
        try:
            with anyio.fail_after(self.timeout_seconds):
                return await self.wrapped.call_tool(name, tool_args, ctx, tool)
        except TimeoutError:
            raise ModelRetry(f"Tool timed out after {self.timeout_seconds} seconds.") from None


def build_agent(model: Model, system_prompt: str, toolsets: list[object] | None, budget: RunBudget) -> Agent:
    resolved = list(toolsets or [])
    if budget.tool_timeout_seconds is not None:
        resolved = [_TimeoutToolset(wrapped=ts, timeout_seconds=budget.tool_timeout_seconds) for ts in resolved]
    return Agent(model, system_prompt=system_prompt, toolsets=resolved or None)


def build_run_kwargs(
    prompt_profile: PromptProfile | None,
    history: list[ChatHistoryMessage] | None,
    budget: RunBudget,
) -> dict[str, object]:
    run_kwargs: dict[str, object] = {}
    temperature = prompt_profile.temperature if prompt_profile else None
    if temperature is not None:
        run_kwargs["model_settings"] = ModelSettings(temperature=temperature)
    message_history = build_message_history(trim_history(history, budget.history_max_chars))
    if message_history:
        run_kwargs["message_history"] = message_history
    if budget.usage_limits is not None:
        run_kwargs["usage_limits"] = budget.usage_limits
    request_id = current_request_id()
    if request_id:
        # 写入 agent run span 属性，便于按 Java requestId 串联整条调用链
        run_kwargs["metadata"] = {"request_id": request_id}
    return run_kwargs


async def run_chat(
    model: Model,
    *,
    site_id: str,
    message: str,
    attachments: list[ChatAttachment] | None = None,
    prompt_profile: PromptProfile | None = None,
    history: list[ChatHistoryMessage] | None = None,
    retrieval_context: list[str] | None = None,
    retrieval_chunks: list[RetrievedChunk] | None = None,
    skill_instructions: str | None = None,
    initial_todos: list[TodoItem] | None = None,
    toolsets: list[object] | None = None,
    budget: RunBudget | None = None,
    locale: str | None = None,
    skills_max_instruction_chars: int | None = None,
    skill_ids: list[str] | None = None,
    skills_missing: list[str] | None = None,
    harness_dir: str | None = None,
    memories: list[str] | None = None,
    memory_enabled: bool = False,
    memory_extraction_enabled: bool = True,
) -> ChatOutcome:
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
        skill_ids=skill_ids,
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
    run_kwargs = build_run_kwargs(prompt_profile, list(harness.memory), resolved_budget)
    user_prompt = build_user_prompt(harness.user_message, attachments)

    needs_mcp_context = execution_needs_agent_context(toolsets or [])
    try:
        agent = build_agent(model, harness.system_prompt, toolsets, resolved_budget)
        if needs_mcp_context:
            # 设计 §19.5：async with agent；用 entered 区分「连接失败」与「进入后运行失败」
            entered = False
            try:
                async with agent:
                    entered = True
                    result = await agent.run(user_prompt, **run_kwargs)
            except UsageLimitExceeded:
                raise
            except Exception as exc:  # noqa: BLE001
                if not entered:
                    return ChatBlocked(
                        message=mcp_unavailable_message(exc),
                        error_code="MCP_UNAVAILABLE",
                    )
                logger.warning("AI provider call failed after MCP connect", exc_info=exc)
                return ChatBlocked(message=external_failure_message("AI provider call failed", exc))
        else:
            result = await agent.run(user_prompt, **run_kwargs)
    except UsageLimitExceeded as exc:
        # 预算超限不是外部依赖故障：单独错误码，便于调用方区分与告警
        return ChatBlocked(message=budget_exceeded_message(exc), error_code=BUDGET_EXCEEDED)
    except Exception as exc:  # noqa: BLE001 — Provider 调用失败 → 诚实 503，不伪造回复
        logger.warning("AI provider call failed", exc_info=exc)
        return ChatBlocked(message=external_failure_message("AI provider call failed", exc))

    text = str(result.output).strip()
    if not text:
        return ChatBlocked(message="AI provider returned an empty response.")
    # 独立 Agent，不传入本轮 UsageLimits，抽取不计入 run budget。
    extracted: list[str] = []
    if memory_enabled and memory_extraction_enabled:
        extracted = await extract_memories(
            model,
            user_message=message,
            assistant_reply=text,
            existing=memories,
        )
    return ChatReply(
        reply=text,
        usage=usage_to_api(result.usage),
        tools=tool_trace_from_messages(result.new_messages()),
        citations=grounding_citations(retrieval_context, retrieval_chunks),
        extracted_memories=extracted,
    )
