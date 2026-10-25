"""一次请求的只读 harness。

对照 Harness² 的组件树，但只组装、不探测、不改写下一轮：
background / guardrails / tool guidance 来自平台目录，skills 正文来自已解析的
技能，MCP 只记服务器名。模型看到的系统提示就是这份组装结果。
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.schemas.chat import ChatHistoryMessage, PromptProfile
from agent_services.schemas.todo import TodoItem

if TYPE_CHECKING:
    from agent_services.agent.runtime import RunBudget

_COMPONENT_FILES = ("background.md", "guardrails.md", "tool_guidance.md")
_COMPONENT_MAX_CHARS = 4_000
_TRUNCATED = "[... truncated]"
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
    site_id: str,
    message: str,
    prompt_profile: PromptProfile | None = None,
    retrieval_context: list[str] | None = None,
    skill_instructions: str | None = None,
    skill_ids: list[str] | None = None,
    skills_missing: list[str] | None = None,
    history: list[ChatHistoryMessage] | None = None,
    todos: list[TodoItem] | None = None,
    locale: str | None = None,
    skills_max_instruction_chars: int | None = None,
    toolsets: list[object] | None = None,
    budget: RunBudget | None = None,
    harness_dir: str | None = None,
) -> RequestHarness:
    from agent_services.agent.runtime import RunBudget, trim_history

    resolved_budget = budget or RunBudget()
    guardrails = _guardrails(resolved_budget, skills_max_instruction_chars)
    memory = tuple(trim_history(history, resolved_budget.history_max_chars))
    site_prompt, user_message = build_prompt_parts(
        site_id,
        message,
        prompt_profile,
        retrieval_context,
        skill_instructions=skill_instructions,
        todos=todos,
        locale=locale,
        skills_max_instruction_chars=skills_max_instruction_chars,
    )
    root = Path(harness_dir) if harness_dir else Path(default_harness_directory())
    background = _read_component(root, "background.md")
    guardrails_text = _read_component(root, "guardrails.md")
    tool_file = _read_component(root, "tool_guidance.md")
    tool_guidance, mcp_servers = _inspect_toolsets(toolsets or [])
    system_prompt = _compose(
        background=background,
        guardrails_text=guardrails_text,
        limits_text=_limits_text(guardrails),
        site_prompt=site_prompt,
        tool_file=tool_file,
        tool_guidance=tool_guidance,
        mcp_servers=mcp_servers,
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
    tool_file: str,
    tool_guidance: tuple[ToolGuidance, ...],
    mcp_servers: tuple[str, ...],
) -> str:
    sections = [part for part in (background, guardrails_text, limits_text, site_prompt, tool_file) if part]
    if tool_guidance:
        lines = [
            f"- {item.name}: {item.description}" if item.description else f"- {item.name}"
            for item in tool_guidance
        ]
        sections.append("# Registered tools\n\n" + "\n".join(lines))
    if mcp_servers:
        sections.append("# MCP servers\n\n" + "\n".join(f"- {name}" for name in mcp_servers))
    return "\n\n".join(sections)


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
