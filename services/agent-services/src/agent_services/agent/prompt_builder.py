import re
from datetime import datetime, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from agent_services.agent.skills import limit_skill_instructions
from agent_services.schemas.chat import PromptProfile
from agent_services.schemas.todo import TodoItem

# 只接受语言标签，避免把任意文本写进 system prompt
_LOCALE_RE = re.compile(r"^[A-Za-z]{2,3}(?:[_-][A-Za-z0-9]{2,8}){0,3}$")


def build_prompt_parts(
    agent_id: str,
    user_message: str,
    prompt_profile: PromptProfile | None = None,
    retrieval_context: list[str] | None = None,
    skill_instructions: str | None = None,
    todos: list[TodoItem] | None = None,
    locale: str | None = None,
    skills_max_instruction_chars: int | None = None,
    knowledge_available: bool = False,
    timezone_name: str | None = None,
    now: datetime | None = None,
) -> tuple[str, str]:
    system = (
        prompt_profile.system_prompt.strip()
        if prompt_profile and prompt_profile.system_prompt and prompt_profile.system_prompt.strip()
        else f"You are the assistant for site: {agent_id}. Answer in a concise and helpful way."
    )
    # 事实锚定（常开）：关于本站的陈述只能来自工具结果与既有上下文；
    # category 只是组织用标签、不是行业定性；工具没覆盖的就说不可用，禁止臆造站点业务与身份。
    system = (
        f"{system}\n\n"
        "Describe this site only from tool results and the provided context. "
        "The site's category is just an organizational tag, not a business classification. "
        "If no tool data covers something, say it is not available instead of guessing."
    )
    if knowledge_available:
        # 只在本轮确实注册了 search_site_knowledge 时才写，避免提示与工具不一致。
        # 覆盖面刻意写宽：实测中「VPN 的远程网关是什么」这类问题被模型当成通识
        # 问题绕过工具、凭参数知识作答，而站点手册里其实有站点专属值——凡是可能
        # 被知识库覆盖的事实（含配置值与术语）都必须先查，站点值优先于通识。
        system = (
            f"{system}\n\n"
            "When the user asks about this site's documents or facts — or anything "
            "the knowledge base might cover, such as procedures, configuration "
            "values (hosts, gateways, addresses, versions, prices, hours), or "
            "terminology in the site's subject area — use the search_site_knowledge "
            "tool first and answer from the returned passages, even if you think "
            "you already know the answer: a site-specific value in the documents "
            "takes precedence over general knowledge. If the search returns nothing "
            "relevant, say you could not find it in the site's documents instead of "
            "answering from general knowledge. If the question has several distinct "
            "parts, run a separate search for each part instead of merging them "
            "into one query."
        )
    if skill_instructions:
        text = skill_instructions
        if skills_max_instruction_chars is not None:
            text = limit_skill_instructions(text, skills_max_instruction_chars)
        if text:
            system = f"{system}\n\n# Active skills\n\n{text}"
    if todos:
        lines = "\n".join(f"- [{item.status}] {item.id}: {item.title}" for item in todos)
        system = f"{system}\n\n# Current todos (from trusted context)\n{lines}"
    if retrieval_context:
        knowledge = "\n\n".join(f"- {chunk}" for chunk in retrieval_context)
        system = (
            f"{system}\n\n"
            "Relevant site knowledge (retrieved from the site knowledge base; "
            "prefer it when answering):\n"
            f"{knowledge}"
        )
    # 与 locale 行同层，独立成段，不写进站点 Prompt，也不进 harness 平台文件。
    # #159 定位、#160 身份在 session_context_section 里追加，本段目前只有时间。
    moment = now if now is not None else datetime.now(timezone.utc)
    system = f"{system}\n\n{session_context_section(timezone_name, moment)}"
    if locale and _LOCALE_RE.fullmatch(locale.strip()):
        system = f"{system}\n\nReply in this locale: {locale.strip()}."
    return system, user_message.strip()


_WEEKDAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")


def session_context_section(timezone_name: str | None, now: datetime) -> str:
    """会话上下文段。后续单只往 blocks 里加块，不改时间块的渲染。"""
    blocks = [_time_context_block(timezone_name, now)]
    return "\n\n".join(blocks)


def _time_context_block(timezone_name: str | None, now: datetime) -> str:
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    zone, fallback = _resolve_zone(timezone_name)
    local = now.astimezone(zone)
    lines = [
        "# Session context",
        "",
        f"Local date: {local.strftime('%Y-%m-%d')}",
        f"Weekday: {_WEEKDAYS[local.weekday()]}",
        f"Local time: {local.strftime('%H:%M')}",
        f"Timezone: {zone.key}",
    ]
    if fallback:
        lines.append("Timezone fallback: UTC")
    return "\n".join(lines)


def _resolve_zone(name: str | None) -> tuple[ZoneInfo, bool]:
    """非法或缺失一律 UTC，并让调用方标 fallback。不抛给请求处理。"""
    cleaned = name.strip() if isinstance(name, str) else ""
    if not cleaned or len(cleaned) > 64 or any(ch.isspace() for ch in cleaned):
        return ZoneInfo("UTC"), True
    try:
        return ZoneInfo(cleaned), False
    except (ZoneInfoNotFoundError, ValueError, OSError):
        return ZoneInfo("UTC"), True
