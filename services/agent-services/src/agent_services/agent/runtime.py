"""Chat 执行：Pydantic AI Agent 运行（Phase 2 起支持 MULTI-TURN）。

Python 不保存会话状态（stateless between business requests）；
历史由 Java 会话真相源加载后随请求传入，转换为 Pydantic AI message_history。
运行预算（§22.4）：每轮 UsageLimits + 工具超时；超限诚实失败 AGENT_BUDGET_EXCEEDED。
"""

import base64
import json
import logging
import re
from dataclasses import dataclass, field
from difflib import SequenceMatcher

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
from agent_services.agent.knowledge_tools import KnowledgeSearchCollector
from agent_services.agent.memory import extract_memories
from agent_services.agent.skills import SkillDefinition
from agent_services.agent.toolset_factory import execution_needs_agent_context
from agent_services.api.errors import external_failure_message
from agent_services.config import Settings
from agent_services.observability.request_id import current_request_id
from agent_services.retrieval.store import RetrievedChunk, citation_sources
from agent_services.schemas.chat import (
    ChatAttachment,
    ChatDocument,
    ChatHistoryMessage,
    ExperienceLessonItem,
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
    """框架层重试、ToolFailed，或平台工具约定的 {"error": ...} 返回 → 失败。

    2.60 起工具超时与框架重试不再单独产出 RetryPromptPart，而是
    ToolReturnPart(outcome='retried', retry=...)；outcome / retry 防御式读取，
    2.54 对象没有这些字段时不抛错。
    """
    if isinstance(part, RetryPromptPart):
        return False
    # 任一成立即失败：outcome 为 retried / failed，或携带框架重试信息（2.60 为 RetryInfo）。
    # failed 是 ToolFailed 的终态（委派超时、超限等），模型看得到说明，但不再重试。
    outcome = getattr(part, "outcome", None)
    if outcome in {"retried", "failed"} or getattr(part, "retry", None) is not None:
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


# 单份工具返回进语料的上限，防异常大返回（如整表导出）撑爆回查。
_TOOL_TEXT_MAX_CHARS = 100_000


def tool_content_text(content: object) -> str:
    """工具返回内容序列化为语料文本。只在进程内用于 grounding 回查，不落轨迹。"""
    if isinstance(content, str):
        text = content
    else:
        try:
            text = json.dumps(content, ensure_ascii=False, default=str)
        except (TypeError, ValueError):
            text = str(content)
    return text.strip()[:_TOOL_TEXT_MAX_CHARS]


def tool_result_texts(messages: list[ModelMessage]) -> list[str]:
    """从一轮 run 的消息里收全部工具返回文本（R3 统一语料的工具面来源）。"""
    texts: list[str] = []
    for message in messages:
        if not isinstance(message, ModelRequest):
            continue
        for part in message.parts:
            if isinstance(part, ToolReturnPart):
                text = tool_content_text(part.content)
                if text:
                    texts.append(text)
    return texts


def grounding_citations(
    retrieval_context: list[str] | None,
    retrieval_chunks: list[RetrievedChunk] | None,
) -> list[dict[str, object]] | None:
    """本轮确实把 chunks 写进提示时才给出引用；降级或未检索时调用方应传入空上下文。"""
    if not retrieval_context or not retrieval_chunks:
        return None
    citations = citation_sources(retrieval_chunks)
    return citations or None


def reply_citations(
    retrieval_context: list[str] | None,
    retrieval_chunks: list[RetrievedChunk] | None,
    tool_chunks: list[RetrievedChunk] | None,
) -> list[dict[str, object]] | None:
    """本轮引用 = 预取注入的片段 ∪ agent 经知识搜索工具实际命中的片段。

    工具命中的片段经工具返回进入模型上下文，grounding 天然成立，不再要求
    retrieval_context 非空；两路都没有时返回 None（不拼假引用）。
    """
    merged: list[RetrievedChunk] = []
    if retrieval_context and retrieval_chunks:
        merged.extend(retrieval_chunks)
    if tool_chunks:
        merged.extend(tool_chunks)
    if not merged:
        return None
    return citation_sources(merged) or None


# 记录类问句：没查到就不许给出具体值。能力边界题（能否读数据库）不在这张表里。
_RECORD_CUES = (
    "编号",
    "編號",
    "申请",
    "申請",
    "考勤",
    "出勤",
    "工时",
    "工時",
    "订单",
    "訂單",
    "金额",
    "金額",
    "证件",
    "證件",
    "入台证",
    "入台證",
    "状态",
    "狀態",
)
# 「统计」覆盖黄金题「10 月考勤统计」；其余是定稿列出的汇总说法。
_AGGREGATE_CUES = ("多久", "多少", "合计", "合計", "一共", "统计", "統計")
_STATUS_CUES = (
    "待送件",
    "已送件",
    "审核中",
    "審核中",
    "已通过",
    "已通過",
    "驳回",
    "駁回",
    "待审核",
    "待審核",
    "pending",
    "approved",
    "rejected",
)
_DATE_RE = re.compile(r"\d{4}\s*[-/.年]\s*\d{1,2}|\d{1,2}\s*月\s*\d{1,2}\s*日")
_ID_RE = re.compile(r"(?<![A-Za-z0-9])[A-Za-z]{0,8}-?\d{5,}[A-Za-z0-9-]*")
_NUMBER_RE = re.compile(r"\d+(?:\.\d+)?")
_DEGRADED_CUES = ("不完整", "可靠性下降", "degraded", "incomplete")

_REFUSAL = {
    "zh": "这一轮没有查到知识库记录，不能给出具体编号、状态或数字。",
    "en": "No knowledge-base record was retrieved this turn, so I cannot give a specific identifier, status, or number.",
}
_AGGREGATE = {
    "zh": "片段检索无法给出全量统计。",
    "en": "Passage search cannot support a full total.",
}
_DEGRADED_NOTE = {
    "zh": "本次检索结果不完整：至少有一类嵌入失败，可靠性下降。",
    "en": "This search result is incomplete: at least one embedding class failed, so reliability is lower.",
}
_RETRY_RECORD = (
    "You must call search_site_knowledge before stating any identifier, status, date, or number "
    "from the user's records or site documents. Every identifier, status, and date you state must "
    "appear in this turn's tool results or the provided context; drop or correct any value they "
    "do not contain. If the search returns nothing, say so and do not invent a value."
)
_RETRY_AGGREGATE = (
    "Passage search cannot support a full count or total. Do not answer with a summed or counted number "
    "unless that exact number appears in the tool result. Say that passage search cannot support a full total."
)
_RETRY_ACTION = (
    "A tool call you made this turn failed, but your draft claims the action succeeded. "
    "Correct the reply: report honestly what failed and what was not completed. "
    "Only claim success for actions that a tool result in this turn confirms."
)
# R10（Issue #191）：答案整段泄露内部脚手架时的中性固定句，进 _fixed 双语表同口径。
_LEAK = {
    "zh": "这部分属于内部配置，不能提供。",
    "en": "That is internal configuration and cannot be shared.",
}


def fixed_sentence_locale(locale: str | None) -> str:
    """固定句只备 zh/en。其余语言，以及空 locale，回退英文。"""
    if not locale or not locale.strip():
        return "en"
    lang = locale.strip().lower().replace("_", "-").split("-", 1)[0]
    return "zh" if lang == "zh" else "en"


def _fixed(table: dict[str, str], locale: str | None) -> str:
    return table[fixed_sentence_locale(locale)]


def _tool_corpus(
    collector: KnowledgeSearchCollector | None,
    extra_texts: list[str] | tuple[str, ...] = (),
) -> str:
    """本轮语料＝知识片段＋非检索 passages＋调用方补入的全部工具返回/预取语境/历史。

    R3 回查（Issue #191）的合法依据不止知识片段：业务工具、表格工具（已经
    passages 通道）、MCP、委派等工具的返回文本与预取语境都由调用方经
    extra_texts 并入，不并入必误伤合法答案。
    """
    parts: list[str] = []
    if collector is not None:
        for chunk in collector.chunks:
            parts.append(chunk.content)
            if chunk.title:
                parts.append(chunk.title)
        parts.extend(collector.passages)
    parts.extend(text for text in extra_texts if text and text.strip())
    return "\n".join(parts)


# R3 值回查专用日期模式：比 _DATE_RE（只管「有没有日期」）多捕获日，
# 否则同月不同日的值查不出差异。
_VALUE_DATE_RE = re.compile(
    r"\d{4}\s*[-/.年]\s*\d{1,2}(?:\s*[-/.月]\s*\d{1,2}\s*日?)?|\d{1,2}\s*月\s*\d{1,2}\s*日"
)


def _date_keys(match_text: str) -> set[str]:
    """一个日期文本的归一键集合：年前置时含 YYYY-MM 与 YYYY-MM-DD 两级与 MM-DD 后缀。"""
    groups = [int(group) for group in re.findall(r"\d+", match_text)]
    if len(groups) < 2:
        return set()
    first, second = groups[0], groups[1]
    if first >= 1000:
        keys = {f"{first:04d}-{second:02d}"}
        if len(groups) >= 3:
            keys.add(f"{first:04d}-{second:02d}-{groups[2]:02d}")
            keys.add(f"{second:02d}-{groups[2]:02d}")
        return keys
    return {f"{first:02d}-{second:02d}"}


def _most_specific_date_key(match_text: str) -> str | None:
    """答案侧日期的最具体键：全文日期须以全文键命中语料，只到年月者以年月键命中。"""
    groups = [int(group) for group in re.findall(r"\d+", match_text)]
    if len(groups) < 2:
        return None
    first, second = groups[0], groups[1]
    if first >= 1000:
        if len(groups) >= 3:
            return f"{first:04d}-{second:02d}-{groups[2]:02d}"
        return f"{first:04d}-{second:02d}"
    return f"{first:02d}-{second:02d}"


def _unsupported_record_value(reply: str, corpus: str) -> bool:
    """答案里的日期/ID/状态值是否有一项不在语料中（R3 已搜索场景回查）。

    日期按归一键比对，吸收「2026-02-03」与「2026年2月3日」的写法差异；
    ID 原样比对；状态按线索词（大小写不敏感）比对。
    """
    corpus_date_keys: set[str] = set()
    for match in _VALUE_DATE_RE.finditer(corpus):
        corpus_date_keys |= _date_keys(match.group(0))
    for match in _VALUE_DATE_RE.finditer(reply):
        key = _most_specific_date_key(match.group(0))
        if key is not None and key not in corpus_date_keys:
            return True
    for match in _ID_RE.finditer(reply):
        if match.group(0) not in corpus:
            return True
    folded = corpus.casefold()
    reply_folded = reply.casefold()
    return any(
        cue.casefold() in reply_folded and cue.casefold() not in folded for cue in _STATUS_CUES
    )


def _is_record_question(message: str) -> bool:
    return any(cue in message for cue in _RECORD_CUES)


def _is_aggregate_question(message: str) -> bool:
    return any(cue in message for cue in _AGGREGATE_CUES)


def _has_record_value(reply: str) -> bool:
    folded = reply.casefold()
    if _DATE_RE.search(reply) or _ID_RE.search(reply):
        return True
    return any(cue.casefold() in folded for cue in _STATUS_CUES)


def _unsupported_number(reply: str, question: str, corpus: str) -> bool:
    for match in _NUMBER_RE.finditer(reply):
        token = match.group(0)
        if token in question or token in corpus:
            continue
        return True
    return False


def _has_degraded_cue(reply: str) -> bool:
    folded = reply.casefold()
    return any(cue.casefold() in folded for cue in _DEGRADED_CUES)


# --- R5（Issue #191）：[cN] 越界标签确定性剥离 -------------------------------

_CITE_LABEL_RE = re.compile(r" ?\[c(\d+)\]")


def _valid_cite_numbers(
    collector: KnowledgeSearchCollector | None,
    retrieval_context: list[str] | None,
    retrieval_chunks: list[RetrievedChunk] | None,
) -> set[int] | None:
    """本轮已分配的引用编号集；本轮根本没有编号来源时返回 None（不动作）。

    编号单一来源与 reply_citations 同口径：collector 累积分配的编号 ∪
    预取注入（retrieval_context 非空时）的片段编号。
    """
    if collector is None and not (retrieval_context and retrieval_chunks):
        return None
    valid: set[int] = set()
    if collector is not None:
        valid.update(chunk.cite for chunk in collector.chunks if chunk.cite is not None)
    if retrieval_context and retrieval_chunks:
        valid.update(chunk.cite for chunk in retrieval_chunks if chunk.cite is not None)
    return valid


def sanitize_citation_labels(
    reply: str, valid_cites: set[int] | None
) -> tuple[str, int, bool]:
    """剥离编号不在已分配集内的 [cN] 标签。返回 (文本, 剥离数, 是否出现合法标签)。

    valid_cites 为 None 表示本轮无编号来源、不判定；合法标签逐字保留，
    剥离只去掉标签本身（连同至多一个前置空格），不伤正文。
    """
    if valid_cites is None:
        return reply, 0, False
    stripped = 0
    has_valid = False

    def _replace(match: re.Match[str]) -> str:
        nonlocal stripped, has_valid
        if int(match.group(1)) in valid_cites:
            has_valid = True
            return match.group(0)
        stripped += 1
        return ""

    return _CITE_LABEL_RE.sub(_replace, reply), stripped, has_valid


# --- R10（Issue #191）：系统提示泄露一票否决 --------------------------------

_LEAK_MIN_SUBSTRING = 40
# 内部特征串：命中任一即视为泄露。都是内部脚手架原文，正常答案不会包含。
_LEAK_FEATURES = (
    "# Run limits",
    BUDGET_EXCEEDED,
    "EXTERNAL_DEPENDENCY_BLOCKED",
    "MCP_UNAVAILABLE",
    "[... truncated]",
)


def _normalize_ws(text: str) -> str:
    return " ".join(text.split())


def detect_prompt_leak(reply: str, platform_texts: tuple[str, ...]) -> str | None:
    """答案与任一平台文件存在 ≥40 字连续公共子串，或含内部特征串 → 泄露。

    返回命中说明（特征串名或组件序号＋长度），不回显原文；未命中返回 None。
    空白归一化后比对，吸收换行/缩进差异；改写或短引（<40 字）不误伤。
    """
    for feature in _LEAK_FEATURES:
        if feature in reply:
            return f"feature:{feature}"
    normalized = _normalize_ws(reply)
    if len(normalized) < _LEAK_MIN_SUBSTRING:
        return None
    for index, source in enumerate(platform_texts):
        cleaned = _normalize_ws(source)
        if len(cleaned) < _LEAK_MIN_SUBSTRING:
            continue
        match = SequenceMatcher(None, normalized, cleaned, autojunk=False).find_longest_match()
        if match.size >= _LEAK_MIN_SUBSTRING:
            return f"component:{index}:{match.size}"
    return None


# --- R2（Issue #191）：失败的动作类工具＋成功声称 → 定向重试（保守版） -------

# 本仓一方工具全集（与 toolset_factory 的注册面同步维护）：不在此列的工具名
# 按 MCP 工具对待（MCP 工具名由服务器定义、本仓无法枚举）。新加一方工具时
# 须同步此表；漏登记的方向是「按动作类多查」，不是漏查。
_FIRST_PARTY_TOOLS = frozenset(
    {
        "search_site_knowledge",
        "list_knowledge_documents",
        "get_site_info",
        "list_site_contents",
        "query_site_table",
        "get_weather",
        "render_chart",
        "list_todos",
        "add_todo",
        "update_todo_status",
        "complete_todo",
        "delegate_task",
        "spawn_background_task",
        "get_background_task",
        "list_background_tasks",
        "cancel_background_task",
    }
)
# 动作类：todo 变更与委派/后台派发。纯读工具失败已有各自诚实口径，不在此列。
_ACTION_TOOLS = frozenset(
    {
        "add_todo",
        "update_todo_status",
        "complete_todo",
        "delegate_task",
        "spawn_background_task",
        "cancel_background_task",
    }
)
_SUCCESS_CLAIM_CUES = (
    "已完成",
    "已提交",
    "已创建",
    "已建立",
    "已保存",
    "已发送",
    "已删除",
    "已更新",
    "已取消",
    "提交成功",
    "创建成功",
    "保存成功",
    "发送成功",
    "成功创建",
    "成功提交",
    "成功保存",
    "successfully created",
    "successfully submitted",
    "successfully saved",
    "successfully sent",
    "completed successfully",
    "has been created",
    "has been submitted",
    "have been created",
    "have been submitted",
)
# 失败/否定说明线索：出现任一即视为已如实交代，不触发（转述与否定句靠此排除）。
_FAILURE_MENTION_CUES = (
    "失败",
    "失敗",
    "未成功",
    "没有成功",
    "沒有成功",
    "并未",
    "並未",
    "未完成",
    "无法",
    "無法",
    "没能",
    "沒能",
    "未能",
    "出错",
    "错误",
    "錯誤",
    "error",
    "failed",
    "failure",
    "unable",
    "could not",
    "couldn't",
    "not successful",
    "was not completed",
    "did not complete",
    "didn't complete",
)


def _is_action_tool(name: str) -> bool:
    return name in _ACTION_TOOLS or name not in _FIRST_PARTY_TOOLS


def _unsupported_success_claim(
    reply: str, tools: list[dict[str, object]] | None
) -> bool:
    """本轮有动作类工具失败、答案声称成功、且通篇没有失败/未完成说明。"""
    if not tools:
        return False
    failed_action = any(
        tool.get("ok") is False and _is_action_tool(str(tool.get("name", "")))
        for tool in tools
    )
    if not failed_action:
        return False
    folded = reply.casefold()
    if not any(cue.casefold() in folded for cue in _SUCCESS_CLAIM_CUES):
        return False
    return not any(cue.casefold() in folded for cue in _FAILURE_MENTION_CUES)


def plan_search_grounding(
    message: str,
    reply: str,
    collector: KnowledgeSearchCollector | None,
    locale: str | None,
    *,
    allow_retry: bool,
    extra_corpus: list[str] | tuple[str, ...] = (),
    tools: list[dict[str, object]] | None = None,
) -> tuple[str, str]:
    """返回 (action, reply)。

    action 为 keep / retry_record / retry_aggregate / retry_action / refuse / aggregate。
    allow_retry 为假时不再要求重跑：记录/汇总类直接换成固定句；R2 动作类
    再犯保留原文、仅记日志（保守口径，Issue #191）。降级说明加在最终回复末尾。
    extra_corpus 是本轮全部工具返回文本、预取语境与历史（R3 回查的合法依据）；
    tools 是本轮工具轨迹（R2 判定用），缺省不查 R2。
    """
    corpus = _tool_corpus(collector, extra_corpus)
    searched = bool(collector is not None and collector.searches)
    action = "keep"
    text = reply
    # 记录类：零搜索就写出编号/日期/状态，或已搜索但值不在本轮语料（含问题原文）
    # 中，都不许保留。优先于汇总数字：含「多少」的证件题要拒绝句，不是统计句。
    if _is_record_question(message) and _has_record_value(reply) and (
        not searched or _unsupported_record_value(reply, corpus + "\n" + message)
    ):
        if allow_retry:
            return "retry_record", reply
        action = "refuse"
        text = _fixed(_REFUSAL, locale)
    elif _is_aggregate_question(message) and _unsupported_number(reply, message, corpus):
        if allow_retry:
            return "retry_aggregate", reply
        action = "aggregate"
        text = _fixed(_AGGREGATE, locale)
    if action == "keep" and _unsupported_success_claim(reply, tools):
        if allow_retry:
            logger.info("guardrail_hit rule=R2 action=retry")
            return "retry_action", reply
        logger.warning("guardrail_hit rule=R2 action=kept_repeat_offense")
    if collector is not None and collector.degraded and not _has_degraded_cue(text):
        text = text.rstrip() + "\n\n" + _fixed(_DEGRADED_NOTE, locale)
    return action, text


def retry_instruction(action: str) -> str | None:
    if action == "retry_record":
        return _RETRY_RECORD
    if action == "retry_aggregate":
        return _RETRY_AGGREGATE
    if action == "retry_action":
        return _RETRY_ACTION
    return None


def remaining_run_budget(
    budget: RunBudget, used: dict[str, int], *, needs_tool: bool
) -> RunBudget | None:
    """第二轮计入同一 RunBudget。请求次数或 token 不够，或记录类重试已无工具次数时，不重跑。"""
    limits = budget.usage_limits
    if limits is None:
        return budget

    def left(limit: int | None, consumed: int) -> int | None:
        if limit is None:
            return None
        return limit - consumed

    requests_left = left(limits.request_limit, int(used.get("requests", 0)))
    tools_left = left(limits.tool_calls_limit, int(used.get("toolCalls", 0)))
    tokens_used = int(used.get("inputTokens", 0)) + int(used.get("outputTokens", 0))
    tokens_left = left(limits.total_tokens_limit, tokens_used)
    if requests_left is not None and requests_left < 1:
        return None
    if tokens_left is not None and tokens_left < 1:
        return None
    if needs_tool and tools_left is not None and tools_left < 1:
        return None
    return RunBudget(
        usage_limits=UsageLimits(
            request_limit=requests_left,
            tool_calls_limit=tools_left,
            total_tokens_limit=tokens_left,
        ),
        tool_timeout_seconds=budget.tool_timeout_seconds,
        history_max_chars=budget.history_max_chars,
    )


def add_usage(first: dict[str, int], second: dict[str, int]) -> dict[str, int]:
    keys = ("requests", "toolCalls", "inputTokens", "outputTokens")
    return {key: int(first.get(key, 0)) + int(second.get(key, 0)) for key in keys}


@dataclass(frozen=True)
class ChatReply:
    reply: str
    usage: dict[str, int] = field(default_factory=dict)
    tools: list[dict[str, object]] = field(default_factory=list)
    citations: list[dict[str, object]] | None = None
    extracted_memories: list[str] = field(default_factory=list)
    # 实际产生回复的模型名（Provider 回报的 model_name）；与请求 spec 不同时即发生过备援切换
    model: str | None = None


def model_identity_line(model: object) -> str | None:
    """给系统提示注入本轮模型身份（Issue #145 F3）。

    模型自报身份原本只有训练先验；注入后至少与实际驱动模型一致。
    FallbackModel 等包装取不到单一名字时不注入，不编造。
    """
    name = getattr(model, "model_name", None)
    if not isinstance(name, str) or not name.strip():
        return None
    # pydantic-ai FallbackModel 的 model_name 是合成名（fallback:a,b），不是任何
    # 真实模型：注入它会让模型自称一串假名，整条链有备援时宁可不注入。
    if name.strip().lower().startswith("fallback:"):
        return None
    return (
        f"You are currently powered by the model '{name.strip()}'. "
        "If the user asks which model you are, answer with this name."
    )


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


def _document_section(document: ChatDocument) -> str:
    """本轮文档上下文：以明确边界包住抽取文本，让模型知道这是用户上传的资料而非指令。"""
    section = f"[用户在本轮上传了文件「{document.name}」，以下为其文字内容]\n{document.text}"
    if document.truncated:
        section += "\n[注：该文件较长，以上仅为前段内容，后段已省略]"
    return section + f"\n[文件「{document.name}」内容结束]"


def build_user_prompt(
    text: str,
    attachments: list[ChatAttachment] | None,
    documents: list[ChatDocument] | None = None,
) -> UserPrompt:
    """无附件时原样返回字符串。有图时为 [非空文字] + 每张图一个 BinaryContent。

    文档（已抽取文本）以文本段追加在图片之后，仅作用于本轮。
    """
    images = [
        BinaryContent(data=base64.b64decode(item.data), media_type=item.media_type)
        for item in attachments or []
    ]
    sections = [_document_section(item) for item in documents or []]
    if not images and not sections:
        return text
    parts: list[str | BinaryContent] = []
    if text:
        parts.append(text)
    parts.extend(images)
    parts.extend(sections)
    return parts


def _user_prompt_texts(user_prompt: UserPrompt) -> list[str]:
    """用户提示的纯文本部分（问题原文＋上传文档段），同属「所给上下文」。"""
    if isinstance(user_prompt, str):
        return [user_prompt]
    return [part for part in user_prompt if isinstance(part, str)]


MAX_HISTORY_IMAGES = 4
IMAGE_ONLY_PLACEHOLDER = "[图片]"


def build_message_history(history: list[ChatHistoryMessage] | None) -> list[ModelMessage]:
    """Java 传入的扁平历史 → Pydantic AI ModelMessage；顺序保持原样。

    图片重播设全域上限：从最新往前数，最多带最近 MAX_HISTORY_IMAGES 张；
    更早的 USER 轮次丢图保文字，文字为空时以占位符保留该轮，
    避免长对话每轮重送全部历史图片。
    """
    if not history:
        return []
    effective: list[list[ChatAttachment] | None] = [item.attachments for item in history]
    image_budget = MAX_HISTORY_IMAGES
    for index in range(len(history) - 1, -1, -1):
        item = history[index]
        if item.role != "USER" or not item.attachments:
            continue
        if image_budget <= 0:
            effective[index] = []
            continue
        take = item.attachments[:image_budget]
        effective[index] = take
        image_budget -= len(take)
    messages: list[ModelMessage] = []
    for index, item in enumerate(history):
        if item.role == "USER":
            attachments = effective[index]
            dropped_images = len(item.attachments or []) - len(attachments or [])
            text = item.content
            if dropped_images > 0 and not attachments and not text:
                text = IMAGE_ONLY_PLACEHOLDER
            messages.append(
                ModelRequest(parts=[UserPromptPart(content=build_user_prompt(text, attachments))])
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
        # delegate_task 自己把超时收成工具返回。再套一层会变成 ModelRetry，委派失败会拖垮本轮。
        resolved = [
            ts
            if getattr(ts, "skip_outer_tool_timeout", False)
            else _TimeoutToolset(wrapped=ts, timeout_seconds=budget.tool_timeout_seconds)
            for ts in resolved
        ]
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
    agent_id: str,
    message: str,
    attachments: list[ChatAttachment] | None = None,
    documents: list[ChatDocument] | None = None,
    prompt_profile: PromptProfile | None = None,
    history: list[ChatHistoryMessage] | None = None,
    retrieval_context: list[str] | None = None,
    retrieval_chunks: list[RetrievedChunk] | None = None,
    skill_instructions: str | None = None,
    initial_todos: list[TodoItem] | None = None,
    toolsets: list[object] | None = None,
    budget: RunBudget | None = None,
    locale: str | None = None,
    timezone_name: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
    accuracy_meters: float | None = None,
    skills_max_instruction_chars: int | None = None,
    skill_ids: list[str] | None = None,
    skills_missing: list[str] | None = None,
    harness_dir: str | None = None,
    memories: list[str] | None = None,
    memory_enabled: bool = False,
    memory_extraction_enabled: bool = True,
    experience_lessons: list[ExperienceLessonItem] | None = None,
    experience_lessons_enabled: bool = True,
    knowledge_collector: KnowledgeSearchCollector | None = None,
    skill_catalog: list[SkillDefinition] | None = None,
    skills_self_select_enabled: bool = False,
) -> ChatOutcome:
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
        skill_ids=skill_ids,
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
    )
    run_kwargs = build_run_kwargs(prompt_profile, list(harness.memory), resolved_budget)
    user_prompt = build_user_prompt(harness.user_message, attachments, documents)

    needs_mcp_context = execution_needs_agent_context(toolsets or [])
    try:
        identity = model_identity_line(model)
        agent = build_agent(
            model,
            f"{harness.system_prompt}\n\n{identity}" if identity else harness.system_prompt,
            toolsets,
            resolved_budget,
        )
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
    usage = usage_to_api(result.usage)
    tools = tool_trace_from_messages(result.new_messages())
    identity = model_identity_line(model)
    base_prompt = f"{harness.system_prompt}\n\n{identity}" if identity else harness.system_prompt
    text, clear_citations, usage, tools = await _apply_search_grounding(
        model=model,
        message=message,
        reply=text,
        locale=locale,
        collector=knowledge_collector,
        budget=resolved_budget,
        usage=usage,
        tools=tools,
        base_prompt=base_prompt,
        user_prompt=user_prompt,
        toolsets=toolsets,
        prompt_profile=prompt_profile,
        history=list(harness.memory),
        needs_mcp_context=needs_mcp_context,
        corpus_texts=[*tool_result_texts(result.new_messages()), *_user_prompt_texts(user_prompt)],
        platform_texts=harness.platform_texts,
        retrieval_context=retrieval_context,
        retrieval_chunks=retrieval_chunks,
    )
    # 独立 Agent，不传入本轮 UsageLimits，抽取不计入 run budget。
    extracted: list[str] = []
    if memory_enabled and memory_extraction_enabled:
        extracted = await extract_memories(
            model,
            user_message=message,
            assistant_reply=text,
            existing=memories,
        )
    served = getattr(getattr(result, "response", None), "model_name", None)
    citations = None
    if not clear_citations:
        citations = reply_citations(
            retrieval_context,
            retrieval_chunks,
            knowledge_collector.chunks if knowledge_collector is not None else None,
        )
    return ChatReply(
        reply=text,
        usage=usage,
        tools=tools,
        citations=citations,
        extracted_memories=extracted,
        model=served.strip() if isinstance(served, str) and served.strip() else None,
    )


async def _apply_search_grounding(
    *,
    model: Model,
    message: str,
    reply: str,
    locale: str | None,
    collector: KnowledgeSearchCollector | None,
    budget: RunBudget,
    usage: dict[str, int],
    tools: list[dict[str, object]],
    base_prompt: str,
    user_prompt: UserPrompt,
    toolsets: list[object] | None,
    prompt_profile: PromptProfile | None,
    history: list[ChatHistoryMessage],
    needs_mcp_context: bool,
    corpus_texts: list[str] | None = None,
    platform_texts: tuple[str, ...] = (),
    retrieval_context: list[str] | None = None,
    retrieval_chunks: list[RetrievedChunk] | None = None,
) -> tuple[str, bool, dict[str, int], list[dict[str, object]]]:
    """跑完再验。预算不够就不重跑，直接用固定句。返回 (回复, 是否清空 citations, 用量, 工具轨迹)。

    plan 之后还有最终出口两道确定性处理（Issue #191）：R5 剥离越界 [cN]
    标签、R10 泄露一票否决——都挂在这里，覆盖第二轮重试产出的答案。
    流式路径同样经此函数：替换只作用于最终文本（DONE reply 与落库文本），
    已发出的 delta 不在此处理范围内（brief 明示的边界）。
    """
    extra_corpus: list[str] = [
        *[item.content for item in history if item.content],
        *(corpus_texts or []),
    ]
    action, _planned = plan_search_grounding(
        message, reply, collector, locale,
        allow_retry=True, extra_corpus=extra_corpus, tools=tools,
    )
    if action in {"retry_record", "retry_aggregate", "retry_action"}:
        instruction = retry_instruction(action)
        nxt = remaining_run_budget(budget, usage, needs_tool=action == "retry_record")
        if nxt is None or instruction is None:
            action, reply = plan_search_grounding(
                message, reply, collector, locale,
                allow_retry=False, extra_corpus=extra_corpus, tools=tools,
            )
        else:
            second = await _run_grounding_retry(
                model=model,
                system_prompt=f"{base_prompt}\n\n{instruction}",
                user_prompt=user_prompt,
                toolsets=toolsets,
                budget=nxt,
                prompt_profile=prompt_profile,
                history=history,
                needs_mcp_context=needs_mcp_context,
            )
            if second is None:
                action, reply = plan_search_grounding(
                    message, reply, collector, locale,
                    allow_retry=False, extra_corpus=extra_corpus, tools=tools,
                )
            else:
                second_text, second_usage, second_tools, second_texts = second
                usage = add_usage(usage, second_usage)
                tools = (tools + second_tools)[:MAX_TOOL_TRACE]
                extra_corpus = [*extra_corpus, *second_texts]
                action, reply = plan_search_grounding(
                    message, second_text, collector, locale,
                    allow_retry=False, extra_corpus=extra_corpus, tools=tools,
                )
    else:
        action, reply = plan_search_grounding(
            message, reply, collector, locale,
            allow_retry=False, extra_corpus=extra_corpus, tools=tools,
        )
    # 最终出口一：R5 引用标签校验（collector 此时含重试轮新记录的片段）。
    valid_cites = _valid_cite_numbers(collector, retrieval_context, retrieval_chunks)
    reply, stripped, has_valid_label = sanitize_citation_labels(reply, valid_cites)
    if stripped:
        logger.warning("guardrail_hit rule=R5 stripped_citations=%d", stripped)
    elif valid_cites and not has_valid_label:
        logger.info(
            "guardrail_hit rule=R5 no_citation_label chunks=%d", len(valid_cites)
        )
    # 最终出口二：R10 泄露一票否决（不重试，整答替换并清 citations）。
    clear_citations = action == "refuse"
    leak = detect_prompt_leak(reply, platform_texts)
    if leak is not None:
        logger.warning("guardrail_hit rule=R10 leak=%s", leak)
        reply = _fixed(_LEAK, locale)
        clear_citations = True
    return reply, clear_citations, usage, tools


async def _run_grounding_retry(
    *,
    model: Model,
    system_prompt: str,
    user_prompt: UserPrompt,
    toolsets: list[object] | None,
    budget: RunBudget,
    prompt_profile: PromptProfile | None,
    history: list[ChatHistoryMessage],
    needs_mcp_context: bool,
) -> tuple[str, dict[str, int], list[dict[str, object]], list[str]] | None:
    """第二轮失败或空回复时返回 None，由调用方改走固定句，不把第一轮未接地的草稿留下。

    返回 (文本, 用量, 工具轨迹, 工具返回文本)：第二轮的工具返回同样并入
    R3 回查语料，模型在重试中查到的值不被误拦。
    """
    run_kwargs = build_run_kwargs(prompt_profile, history, budget)
    agent = build_agent(model, system_prompt, toolsets, budget)
    try:
        if needs_mcp_context:
            async with agent:
                result = await agent.run(user_prompt, **run_kwargs)
        else:
            result = await agent.run(user_prompt, **run_kwargs)
    except UsageLimitExceeded:
        return None
    except Exception:  # noqa: BLE001 — 重试失败不沿用未接地草稿
        logger.warning("search grounding retry failed", exc_info=True)
        return None
    text = str(result.output).strip()
    if not text:
        return None
    messages = result.new_messages()
    return (
        text,
        usage_to_api(result.usage),
        tool_trace_from_messages(messages),
        tool_result_texts(messages),
    )
