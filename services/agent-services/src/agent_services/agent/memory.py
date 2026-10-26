"""从一轮已成功的对话里抽取耐久事实。

抽取使用独立的小 Agent，不接收本轮 UsageLimits，因此不计入 run budget。
任何失败都只记日志并返回空列表，不让对话失败。
"""

from __future__ import annotations

import logging

import anyio
from pydantic_ai import Agent
from pydantic_ai.models import Model

logger = logging.getLogger(__name__)

_MAX_FACTS = 3
_MAX_FACT_CHARS = 80
_MAX_USER_CHARS = 1000
_MAX_REPLY_CHARS = 1500
_TIMEOUT_SECONDS = 10

_SYSTEM_PROMPT = (
    "Extract durable facts about the user that will help future conversations. "
    "Keep identity, background, preferences, long-term goals, and arrangements that keep coming up. "
    "Skip one-off requests and temporary information. "
    "Never extract secrets or credentials: passwords, verification codes, bank or card numbers, "
    "government id numbers, or API keys. "
    "Each fact must be one standalone sentence of at most 80 characters. "
    "Do not repeat a fact already listed under Known facts. "
    "Return at most 3 facts. Return an empty list when nothing is worth keeping."
)


def _clean_fact(value: str) -> str:
    return " ".join(value.split())[:_MAX_FACT_CHARS]


def _known_facts(existing: list[str] | None) -> list[str]:
    known: list[str] = []
    seen: set[str] = set()
    for item in existing or []:
        if not isinstance(item, str):
            continue
        text = _clean_fact(item)
        if not text or text in seen:
            continue
        seen.add(text)
        known.append(text)
    return known


def _user_prompt(user_message: str, assistant_reply: str, known: list[str]) -> str:
    listed = "\n".join(f"- {item}" for item in known) if known else "(none)"
    return (
        f"Known facts:\n{listed}\n\n"
        f"User message:\n{user_message}\n\n"
        f"Assistant reply:\n{assistant_reply}"
    )


def _accept_facts(raw: object, known: list[str]) -> list[str]:
    if not isinstance(raw, list):
        return []
    blocked = set(known)
    accepted: list[str] = []
    for item in raw:
        if not isinstance(item, str):
            continue
        text = _clean_fact(item)
        if not text or text in blocked or text in accepted:
            continue
        accepted.append(text)
        if len(accepted) == _MAX_FACTS:
            break
    return accepted


async def extract_memories(
    model: Model,
    *,
    user_message: str,
    assistant_reply: str,
    existing: list[str] | None,
) -> list[str]:
    user_text = (user_message or "")[:_MAX_USER_CHARS]
    reply_text = (assistant_reply or "")[:_MAX_REPLY_CHARS]
    known = _known_facts(existing)
    try:
        with anyio.fail_after(_TIMEOUT_SECONDS):
            agent = Agent(model, system_prompt=_SYSTEM_PROMPT, output_type=list[str])
            result = await agent.run(_user_prompt(user_text, reply_text, known))
        return _accept_facts(result.output, known)
    except Exception:
        logger.warning("memory extraction failed", exc_info=True)
        return []
