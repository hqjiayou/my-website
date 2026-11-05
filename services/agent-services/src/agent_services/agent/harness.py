"""一次请求的只读 harness。

对照 Harness² 的组件树，但只组装、不探测、不改写下一轮：
background / guardrails / tool guidance 来自平台目录，skills 正文来自已解析的
技能，MCP 只记服务器名。模型看到的系统提示就是这份组装结果。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import TYPE_CHECKING

from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.schemas.chat import (
    ChatHistoryMessage,
    ExperienceLessonItem,
    PromptProfile,
)
from agent_services.schemas.todo import TodoItem

if TYPE_CHECKING:
    from agent_services.agent.runtime import RunBudget
    from agent_services.agent.skills import SkillDefinition

logger = logging.getLogger(__name__)

_COMPONENT_FILES = ("background.md", "guardrails.md", "tool_guidance.md")
_COMPONENT_MAX_CHARS = 4_000
_TRUNCATED = "[... truncated]"
# 经验段上限（Issue #184 UoW-4 定死）：最多 10 条、正文总量最多 2000 字元。
# 正文口径 = 各条渲染行（"L<no>. <lesson>"）以换行连接后的总长，不含段标题。
# 超限时取 lesson_no 最新（最大）的条目至上限内，截断事实记 warning，不静默。
_EXPERIENCE_MAX_ITEMS = 10
_EXPERIENCE_MAX_CHARS = 2_000
# 整份 system prompt 的软告警阈值（Issue #205）：各段硬帽合计 27,000 字元
# （平台三文件 3×4,000＋skills 12,000＋经验 2,000＋历史摘要 1,000），再给
# 站点 Prompt / catalog / 工具清单留约 13,000 的合理余量。超过只记 warning，
# 不截断、不改变组装结果——总量膨胀先有观测，是否立硬帽等真实读数再定。
_PROMPT_WARN_CHARS = 40_000
_HARNESS_ROOT = Path(__file__).resolve().parents[3] / "harness"


@dataclass(frozen=True)
class ToolGuidance:
    name: str
    description: str


@dataclass(frozen=True)
class HarnessGuardrails:
    max_requests: int | None
    max_tool_calls: int | None
    max_total_tokens: int | None
    tool_timeout_seconds: float | None
    history_max_chars: int | None
    skills_max_instruction_chars: int | None


@dataclass(frozen=True)
class RequestHarness:
    system_prompt: str
    user_message: str
    background: str
    skill_ids: tuple[str, ...]
    skills_missing: tuple[str, ...]
    memory: tuple[ChatHistoryMessage, ...]
    tool_guidance: tuple[ToolGuidance, ...]
    mcp_servers: tuple[str, ...]
    guardrails: HarnessGuardrails
    # 三个平台文件原文（background / guardrails / tool_guidance 顺序），供 runtime
    # 在最终出口做系统提示泄露检测（Issue #191 R10）。只是本轮实际读入的文本，
    # 与 system_prompt 的组装同源，不另读文件。
    platform_texts: tuple[str, ...] = ()


def default_harness_directory() -> str:
    """未设置 AGENT_HARNESS_DIR 时的目录。装进镜像后改用 /app/harness。"""
    if _HARNESS_ROOT.is_dir():
        return str(_HARNESS_ROOT)
    deployed = Path("/app/harness")
    if deployed.is_dir():
        return str(deployed)
    return str(_HARNESS_ROOT)


def assemble_harness(
    *,
    agent_id: str,
    message: str,
    prompt_profile: PromptProfile | None = None,
    retrieval_context: list[str] | None = None,
    skill_instructions: str | None = None,
    skill_ids: list[str] | None = None,
    skills_missing: list[str] | None = None,
    history: list[ChatHistoryMessage] | None = None,
    todos: list[TodoItem] | None = None,
    locale: str | None = None,
    timezone_name: str | None = None,
    now: datetime | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
    accuracy_meters: float | None = None,
    skills_max_instruction_chars: int | None = None,
    toolsets: list[object] | None = None,
    budget: RunBudget | None = None,
    harness_dir: str | None = None,
    history_summary: str | None = None,
    memories: list[str] | None = None,
    experience_lessons: list[ExperienceLessonItem] | None = None,
    experience_lessons_enabled: bool = True,
    skill_catalog: list[SkillDefinition] | None = None,
    skills_self_select_enabled: bool = False,
    skill_catalog_exclude_ids: tuple[str, ...] = (),
) -> RequestHarness:
    from agent_services.agent.runtime import RunBudget, trim_history

    resolved_budget = budget or RunBudget()
    guardrails = _guardrails(resolved_budget, skills_max_instruction_chars)
    memory = tuple(trim_history(history, resolved_budget.history_max_chars))
    tool_guidance, mcp_servers = _inspect_toolsets(toolsets or [])
    # 知识可用性直接看本轮 toolsets 里有没有 search_site_knowledge：
    # 同步与流式两条 runtime 都经这里组装，旗标不会与实际注册的工具脱节。
    knowledge_available = any(
        item.name == "search_site_knowledge" or item.name.endswith("_search_site_knowledge")
        for item in tool_guidance
    )
    site_prompt, user_message = build_prompt_parts(
        agent_id,
        message,
        prompt_profile,
        retrieval_context,
        skill_instructions=skill_instructions,
        todos=todos,
        locale=locale,
        timezone_name=timezone_name,
        now=now,
        latitude=latitude,
        longitude=longitude,
        accuracy_meters=accuracy_meters,
        skills_max_instruction_chars=skills_max_instruction_chars,
        knowledge_available=knowledge_available,
    )
    root = Path(harness_dir) if harness_dir else Path(default_harness_directory())
    background = _read_component(root, "background.md")
    guardrails_text = _read_component(root, "guardrails.md")
    tool_file = _read_component(root, "tool_guidance.md")
    system_prompt = _compose(
        background=background,
        guardrails_text=guardrails_text,
        limits_text=_limits_text(guardrails),
        site_prompt=site_prompt,
        catalog_section=_skill_catalog_section(
            skill_catalog,
            enabled=skills_self_select_enabled,
            exclude_ids=skill_catalog_exclude_ids,
        ),
        tool_file=tool_file,
        tool_guidance=tool_guidance,
        mcp_servers=mcp_servers,
        history_summary=_history_summary_section(history_summary),
        memory_section=_memory_section(memories),
        experience_section=_experience_section(
            experience_lessons, enabled=experience_lessons_enabled
        ),
    )
    if len(system_prompt) > _PROMPT_WARN_CHARS:
        logger.warning(
            "assembled system prompt is %d chars, above the %d soft warning threshold",
            len(system_prompt),
            _PROMPT_WARN_CHARS,
        )
    return RequestHarness(
        system_prompt=system_prompt,
        user_message=user_message,
        background=background,
        skill_ids=_clean_ids(skill_ids),
        skills_missing=_clean_ids(skills_missing),
        memory=memory,
        tool_guidance=tool_guidance,
        mcp_servers=mcp_servers,
        guardrails=guardrails,
        platform_texts=(background, guardrails_text, tool_file),
    )


def harness_component_flags(harness_dir: str | None = None) -> dict[str, bool]:
    """健康检查只报告三个平台文件在不在，不返回路径或正文。"""
    root = Path(harness_dir) if harness_dir else Path(default_harness_directory())
    return {
        "background": _component_exists(root, "background.md"),
        "guardrails": _component_exists(root, "guardrails.md"),
        "toolGuidance": _component_exists(root, "tool_guidance.md"),
    }


def _clean_ids(values: list[str] | None) -> tuple[str, ...]:
    return tuple(item.strip() for item in (values or []) if item and item.strip())


def _skill_catalog_section(
    catalog: list[SkillDefinition] | None,
    *,
    enabled: bool,
    exclude_ids: tuple[str, ...] = (),
) -> str:
    """skill catalog 摘要段（Issue #195，渐进披露 L1）。

    开关关闭或 catalog 为空时返回空串，系统提示与未启用时逐字相同。
    每行一个 skill：id — description（空白折平）；登记了 references 的
    追加数量标注。description 是 frontmatter 原文（≤1024），不另截断——
    L1 的价值就在匹配质量，截断会把 Do not use 边界切掉。
    exclude_ids（本轮已预选生效的 skill）不进 catalog：段首文案是
    「available but not loaded」，已加载的列进来既是假话，也会诱导模型
    白跑一趟 load_skill 才被告知已生效。
    """
    if not enabled or not catalog:
        return ""
    excluded = set(exclude_ids)
    lines: list[str] = []
    for definition in sorted(catalog, key=lambda item: item.id):
        if definition.id in excluded:
            continue
        description = " ".join(definition.description.split())
        line = f"- {definition.id} — {description}" if description else f"- {definition.id}"
        if definition.references:
            line += f" (references: {len(definition.references)})"
        lines.append(line)
    if not lines:
        return ""
    return (
        "# Skill catalog\n\n"
        "These skills are available but not loaded. When the task matches a "
        "skill's description, call load_skill with its id to load the full "
        "instructions before doing the work, and follow them. If the loaded "
        "instructions point to a reference file, fetch it with "
        "read_skill_reference. Do not load skills that do not match the task.\n\n"
        + "\n".join(lines)
    )


def _limits_text(guardrails: HarnessGuardrails) -> str:
    lines: list[str] = []
    if guardrails.max_requests is not None:
        lines.append(f"- requests: {guardrails.max_requests}")
    if guardrails.max_tool_calls is not None:
        lines.append(f"- tool calls: {guardrails.max_tool_calls}")
    if guardrails.max_total_tokens is not None:
        lines.append(f"- total tokens: {guardrails.max_total_tokens}")
    if guardrails.tool_timeout_seconds is not None:
        lines.append(f"- tool timeout seconds: {guardrails.tool_timeout_seconds:g}")
    if guardrails.history_max_chars is not None:
        lines.append(f"- history characters: {guardrails.history_max_chars}")
    if not lines:
        return ""
    return "# Run limits\n\n" + "\n".join(lines)


def _compose(
    *,
    background: str,
    guardrails_text: str,
    limits_text: str,
    site_prompt: str,
    catalog_section: str,
    tool_file: str,
    tool_guidance: tuple[ToolGuidance, ...],
    mcp_servers: tuple[str, ...],
    history_summary: str,
    memory_section: str,
    experience_section: str,
) -> str:
    sections = [
        part
        for part in (
            background,
            guardrails_text,
            limits_text,
            history_summary,
            memory_section,
            site_prompt,
            # skill catalog 摘要（L1，Issue #195）紧随站点 Prompt：预选正文
            # （# Active skills）在 site_prompt 内，catalog 是它的未加载续集；
            # 开关关或无 catalog 时为空串被过滤，组装逐字回归。
            catalog_section,
            tool_file,
            # 经验段紧随 tool_guidance 之后（Issue #184 UoW-4 定案位置）；
            # 为空串时被过滤，系统提示与未启用时逐字一致。
            experience_section,
        )
        if part
    ]
    if tool_guidance:
        lines = [
            f"- {item.name}: {item.description}" if item.description else f"- {item.name}"
            for item in tool_guidance
        ]
        sections.append("# Registered tools\n\n" + "\n".join(lines))
    delegation = _delegation_section(tool_guidance)
    if delegation:
        sections.append(delegation)
    if mcp_servers:
        sections.append("# MCP servers\n\n" + "\n".join(f"- {name}" for name in mcp_servers))
    return "\n\n".join(sections)


def _delegation_section(tool_guidance: tuple[ToolGuidance, ...]) -> str:
    """工具未注册时返回空串，系统提示与未启用委派时逐字相同。

    后台口径只在后台工具（spawn_background_task，Issue #188）注册时才追加；
    只有同步委派时整段与旧版逐字一致，两个总开关任一关都不会留下后台字样。
    """
    names = {item.name for item in tool_guidance}
    has_delegate = any(
        name == "delegate_task" or name.endswith("_delegate_task") for name in names
    )
    has_background = any(
        name == "spawn_background_task" or name.endswith("_spawn_background_task")
        for name in names
    )
    if not has_delegate and not has_background:
        return ""
    blocks: list[str] = []
    if has_delegate:
        blocks.append(
            "Use delegate_task for a self-contained sub-question, or when several independent angles "
            "can be checked in parallel.\n"
            "Do not delegate a simple question, or anything that needs a write or other business action.\n"
            "Each delegated task must stand alone; the sub-agent does not see this conversation."
        )
    if has_background:
        blocks.append(
            "Use spawn_background_task instead for a sub-question that is slow or independent "
            "enough that this conversation should not wait for it: it returns a task_id "
            "immediately and the helper keeps working in the background with the same "
            "knowledge-only tools.\n"
            "After spawning a background task, tell the user its task_id and that the result "
            "can be checked later. When the user later asks about its progress or result, call "
            "list_background_tasks or get_background_task first and answer from the returned "
            "state; never guess whether it has finished.\n"
            "Reach for delegate_task when the result is needed in this reply, and for a "
            "background task when it is not."
        )
    return "# Delegation\n\n" + "\n".join(blocks)


def _history_summary_section(summary: str | None) -> str:
    if summary is None or not summary.strip():
        return ""
    return "# Earlier conversation\n\n" + summary.strip()


def _experience_section(
    lessons: list[ExperienceLessonItem] | None, *, enabled: bool
) -> str:
    """已批准经验条目组装段（Issue #184 UoW-4）。

    开关关闭或无有效条目时返回空串，系统提示与未启用时逐字相同。条目按
    lesson_no 升序渲染为 "L<no>. <lesson>"；超限时从最新（编号最大）往旧取
    连续一段至条数/正文总量上限内，被丢弃条数与正文截断都记 warning，不静默。
    单条本身超过正文总量上限时截断该条正文，让最新一条至少以截断形落地。
    """
    if not enabled:
        return ""
    cleaned = sorted(
        (
            (item.lesson_no, item.lesson.strip())
            for item in (lessons or [])
            if item is not None and item.lesson and item.lesson.strip()
        ),
        key=lambda pair: pair[0],
    )
    if not cleaned:
        return ""
    kept_newest_first: list[str] = []
    used = 0
    dropped = 0
    text_truncated = False
    for idx, (lesson_no, text) in enumerate(reversed(cleaned)):
        if len(kept_newest_first) >= _EXPERIENCE_MAX_ITEMS:
            dropped = len(cleaned) - idx
            break
        line = f"L{lesson_no}. {text}"
        cost = len(line) + (1 if kept_newest_first else 0)  # 与下一条之间的换行
        if used + cost <= _EXPERIENCE_MAX_CHARS:
            kept_newest_first.append(line)
            used += cost
            continue
        if not kept_newest_first:
            room = _EXPERIENCE_MAX_CHARS - len(f"L{lesson_no}. ") - len(_TRUNCATED)
            if room > 0:
                kept_newest_first.append(f"L{lesson_no}. {text[:room]}{_TRUNCATED}")
                used = _EXPERIENCE_MAX_CHARS
                text_truncated = True
                continue
        dropped = len(cleaned) - idx
        break
    if dropped or text_truncated:
        logger.warning(
            "experience lessons truncated: kept %d of %d (dropped %d, text_truncated=%s)",
            len(kept_newest_first),
            len(cleaned),
            dropped,
            text_truncated,
        )
    if not kept_newest_first:
        return ""
    body = "\n".join(reversed(kept_newest_first))
    return "# Experience lessons\n\n" + body


def _memory_section(memories: list[str] | None) -> str:
    """无有效条目时返回空串，系统提示与未传 memories 时逐字相同。"""
    lines = [item.strip() for item in (memories or []) if item and item.strip()]
    if not lines:
        return ""
    body = "\n".join(f"- {item}" for item in lines)
    return (
        "# Long-term memory\n\n"
        "These are durable facts about this user. "
        "Refer to them naturally when answering. "
        "Do not recite them one by one. "
        "If they conflict with what the user says in this turn, follow this turn.\n\n"
        + body
    )


def _guardrails(budget: RunBudget, skills_max_instruction_chars: int | None) -> HarnessGuardrails:
    limits = budget.usage_limits
    return HarnessGuardrails(
        max_requests=limits.request_limit if limits else None,
        max_tool_calls=limits.tool_calls_limit if limits else None,
        max_total_tokens=limits.total_tokens_limit if limits else None,
        tool_timeout_seconds=budget.tool_timeout_seconds,
        history_max_chars=budget.history_max_chars,
        skills_max_instruction_chars=skills_max_instruction_chars,
    )


def _read_component(root: Path, name: str) -> str:
    if name not in _COMPONENT_FILES:
        return ""
    try:
        base = root.resolve()
        path = (base / name).resolve()
    except OSError:
        return ""
    if not path.is_relative_to(base) or not path.is_file():
        return ""
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return ""
    text = text.strip()
    if len(text) <= _COMPONENT_MAX_CHARS:
        return text
    logger.warning(
        "harness component %s truncated: %d chars exceeds max %d",
        name,
        len(text),
        _COMPONENT_MAX_CHARS,
    )
    return text[:_COMPONENT_MAX_CHARS] + "\n" + _TRUNCATED


def _component_exists(root: Path, name: str) -> bool:
    if name not in _COMPONENT_FILES:
        return False
    try:
        base = root.resolve()
        path = (base / name).resolve()
    except OSError:
        return False
    return path.is_relative_to(base) and path.is_file()


def _inspect_toolsets(toolsets: list[object]) -> tuple[tuple[ToolGuidance, ...], tuple[str, ...]]:
    guidance: list[ToolGuidance] = []
    mcp_servers: list[str] = []
    seen_tools: set[str] = set()
    seen_mcp: set[str] = set()
    for toolset in toolsets:
        leaf, prefixes = _leaf_and_prefixes(toolset)
        tools = getattr(leaf, "tools", None)
        qualified = "_".join(prefixes)
        if isinstance(tools, dict):
            for key, tool in tools.items():
                raw_name = getattr(tool, "name", None) or key
                if not isinstance(raw_name, str) or not raw_name.strip():
                    continue
                full_name = f"{qualified}_{raw_name}" if qualified else raw_name
                if full_name in seen_tools:
                    continue
                seen_tools.add(full_name)
                description = _summary(getattr(tool, "description", "") or "")
                guidance.append(ToolGuidance(full_name, description))
            continue
        label = qualified or _safe_label(leaf)
        if label and label not in seen_mcp:
            seen_mcp.add(label)
            mcp_servers.append(label)
    return tuple(guidance), tuple(mcp_servers)


def _leaf_and_prefixes(toolset: object) -> tuple[object, list[str]]:
    prefixes: list[str] = []
    current = toolset
    seen: set[int] = set()
    while True:
        if id(current) in seen:
            return current, prefixes
        seen.add(id(current))
        prefix = getattr(current, "prefix", None)
        if isinstance(prefix, str) and prefix.strip():
            prefixes.append(prefix.strip())
        wrapped = getattr(current, "wrapped", None)
        if wrapped is None:
            return current, prefixes
        current = wrapped


def _safe_label(toolset: object) -> str:
    name = getattr(toolset, "name", None)
    if isinstance(name, str) and name.strip() and "://" not in name:
        return name.strip()
    return ""


def _summary(description: str) -> str:
    return " ".join(description.split())[:200]
