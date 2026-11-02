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
    latitude: float | None = None,
    longitude: float | None = None,
    accuracy_meters: float | None = None,
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
    # 钟点用 IANA 时区。位置只来自浏览器或手机定位权限，不从时区推断城市。
    moment = now if now is not None else datetime.now(timezone.utc)
    system = (
        f"{system}\n\n{session_context_section(timezone_name, moment, latitude, longitude, accuracy_meters)}"
    )
    if locale and _LOCALE_RE.fullmatch(locale.strip()):
        system = f"{system}\n\nReply in this locale: {locale.strip()}."
    return system, user_message.strip()


_WEEKDAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")


def session_context_section(
    timezone_name: str | None,
    now: datetime,
    latitude: float | None = None,
    longitude: float | None = None,
    accuracy_meters: float | None = None,
) -> str:
    """会话上下文段。后续单只往 blocks 里加块，不改时间块的渲染。"""
    blocks = [_time_context_block(timezone_name, now, latitude, longitude, accuracy_meters)]
    return "\n\n".join(blocks)


def _time_context_block(
    timezone_name: str | None,
    now: datetime,
    latitude: float | None = None,
    longitude: float | None = None,
    accuracy_meters: float | None = None,
) -> str:
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    zone, fallback = _resolve_zone(timezone_name)
    local = now.astimezone(zone)
    offset = local.strftime("%z")
    offset_label = f"UTC{offset[0]}{offset[1:3]}:{offset[3:5]}" if len(offset) == 5 else "UTC"
    position = _accepted_position(latitude, longitude, accuracy_meters)
    lines = [
        "# Session context",
        "",
        "These fields are the user's current local time. Timezone is only the clock.",
        "If the user asks what time it is or what the date is, answer from Local time, Local date, and Weekday. "
        "Do not convert Local time to UTC.",
    ]
    if position is None:
        lines.append(
            "Location permission was not granted. Timezone is only the clock, not a city. "
            "If the user asks where they are, or for local weather without naming a place, "
            "say the browser or phone has not granted location permission. Do not ask which city. "
            "Weather can only be checked for a shared position, never for a place name. "
            "Do not invent a city from Timezone."
        )
    else:
        lines.extend(
            [
                "Location comes from the browser or phone Geolocation API after the user granted permission. "
                "It is not the timezone.",
                "This position is the user's location. Keep it for the whole thread. Do not retract it.",
                "Do not treat Timezone as the city.",
                "If they ask how you know, say the browser or phone location permission.",
                "If they ask for a finer point than Accuracy meters, say the fix is only that accurate. "
                "Do not claim the position had no basis.",
                "If they ask about local conditions such as weather without naming a place, use this position. "
                "Do not ask which city.",
                "For live weather or a forecast here, call the get_weather tool with no coordinates: "
                "it defaults to this position. Answer from its result, not from general knowledge.",
                "Weather lookup covers only this position and cannot look up other places by name: "
                "if they name another place, say so instead of answering for this position.",
                "If no listed tool can look that up, name this position and say the live data is unavailable.",
            ]
        )
    lines.extend(
        [
            "",
            f"Local date: {local.strftime('%Y-%m-%d')}",
            f"Weekday: {_WEEKDAYS[local.weekday()]}",
            f"Local time: {local.strftime('%H:%M')} ({offset_label})",
            f"Timezone: {zone.key}",
        ]
    )
    if fallback:
        lines.append("Timezone fallback: UTC")
    if position is not None:
        lat, lng, accuracy = position
        lines.append(f"Latitude: {lat:.5f}")
        lines.append(f"Longitude: {lng:.5f}")
        if accuracy is not None:
            lines.append(f"Accuracy meters: {accuracy:.0f}")
    return "\n".join(lines)


def _accepted_position(
    latitude: float | None,
    longitude: float | None,
    accuracy_meters: float | None,
) -> tuple[float, float, float | None] | None:
    """经纬度必须成对且在范围内。精度非法只丢掉精度。"""
    if latitude is None or longitude is None:
        return None
    try:
        lat = float(latitude)
        lng = float(longitude)
    except (TypeError, ValueError):
        return None
    if lat < -90 or lat > 90 or lng < -180 or lng > 180:
        return None
    accuracy: float | None
    try:
        accuracy = None if accuracy_meters is None else float(accuracy_meters)
    except (TypeError, ValueError):
        accuracy = None
    if accuracy is None or accuracy < 0 or accuracy > 100_000:
        accuracy = None
    return lat, lng, accuracy


def _resolve_zone(name: str | None) -> tuple[ZoneInfo, bool]:
    """非法或缺失一律 UTC，并让调用方标 fallback。不抛给请求处理。"""
    cleaned = name.strip() if isinstance(name, str) else ""
    if not cleaned or len(cleaned) > 64 or any(ch.isspace() for ch in cleaned):
        return ZoneInfo("UTC"), True
    try:
        return ZoneInfo(cleaned), False
    except (ZoneInfoNotFoundError, ValueError, OSError):
        return ZoneInfo("UTC"), True
