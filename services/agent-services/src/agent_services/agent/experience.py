"""从带失败信号的对话轮里提炼可复用的「经验教训」（Issue #184 UoW-2）。

与长期记忆（memory.py）是两条独立管线：记忆抽的是 per-user 的用户事实，
这里抽的是平台全局的 agent 教训（失败案例／口径约定／工具使用教训），
产物经 Java 落 agent_experience_lesson 待人审，未批准不下发。

隔离形态仿 memory.py：独立 Agent、不挂工具、不计 run budget、短超时。
区别在失败语义：记忆失败可静默回空（对话已成功），提炼是被显式触发的
批处理，失败必须诚实回错（ExperienceExtractionUnavailable），由 Java 侧
计入批次失败数，不许用空数组冒充「没有可提炼的」。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

import anyio
from pydantic import BaseModel
from pydantic_ai import Agent
from pydantic_ai.models import Model

from agent_services.schemas.experience import ExperienceTurn

logger = logging.getLogger(__name__)

_MAX_CANDIDATES = 8
_MAX_USER_CHARS = 1500
_MAX_REPLY_CHARS = 2500
_MAX_TITLE_CHARS = 120
_MAX_SYMPTOM_CHARS = 1000
_MAX_LESSON_CHARS = 1200
_MAX_EVIDENCE_CHARS = 1000
_MAX_EXISTING_TITLES = 100
_TIMEOUT_SECONDS = 30

_SYSTEM_PROMPT = (
    "You extract reusable experience lessons for an AI assistant from past conversation "
    "turns that Java has pre-selected because they carry failure signals or settled conventions. "
    "Extract ONLY lessons in these three categories: "
    "1. Failure cases: a tool call failed, a run hit a limit, or an answer was wrong and had "
    "to be corrected; the lesson states how to avoid or handle that failure next time. "
    "2. Convention agreements: a caliber, definition, format or interpretation the user and "
    "the assistant settled on (for example which figure counts as the official one); the "
    "lesson states the agreed convention. "
    "3. Tool-use lessons: a durable rule about when or how to use a tool, learned from how "
    "a tool behaved in these turns. "
    "Never extract facts about the user (identity, background, preferences, personal "
    "circumstances): those belong to long-term memory, not to experience lessons. "
    "Skip one-off small talk and requests that teach nothing reusable. "
    "Every candidate must be grounded in one specific input turn: set turnIndex to that "
    "turn's number and write evidence describing what happened in that turn (the signal, "
    "the failed tool, the correction) rather than quoting the user. "
    "Do not propose a lesson whose title is synonymous with any title listed under "
    "Existing lesson titles. "
    "The lesson field must be one or a few imperative rule sentences that can be inserted "
    "verbatim into a system prompt. "
    "Return an empty list when nothing in these turns is worth keeping."
)


class ExperienceExtractionUnavailable(Exception):
    """提炼模型调用失败（超时/上游错误/输出不可解析）：上层映射为 BLOCKED。"""


class _CandidateDraft(BaseModel):
    """模型的结构化输出草案；turn_index 指回输入轮次序号（从 1 起）。"""

    turn_index: int
    title: str
    symptom: str | None = None
    lesson: str
    evidence: str | None = None


@dataclass
class ExtractedCandidate:
    source_turn_id: str
    title: str
    symptom: str | None
    lesson: str
    evidence: str | None


@dataclass
class ExperienceExtraction:
    candidates: list[ExtractedCandidate] = field(default_factory=list)
    usage: dict[str, int] = field(default_factory=dict)


def _clean_text(value: object, limit: int) -> str:
    if not isinstance(value, str):
        return ""
    return " ".join(value.split())[:limit]


def _clean_optional(value: object, limit: int) -> str | None:
    text = _clean_text(value, limit)
    return text or None


def _title_key(title: str) -> str:
    return " ".join(title.split()).casefold()


def _existing_title_keys(existing_titles: list[str] | None) -> set[str]:
    keys: set[str] = set()
    for item in existing_titles or []:
        text = _clean_text(item, _MAX_TITLE_CHARS)
        if text:
            keys.add(_title_key(text))
    return keys


def _user_prompt(turns: list[ExperienceTurn], existing_titles: list[str] | None) -> str:
    titles = []
    for item in existing_titles or []:
        text = _clean_text(item, _MAX_TITLE_CHARS)
        if text:
            titles.append(text)
        if len(titles) == _MAX_EXISTING_TITLES:
            break
    listed = "\n".join(f"- {item}" for item in titles) if titles else "(none)"
    blocks: list[str] = []
    for index, turn in enumerate(turns, start=1):
        user_text = (turn.user_message or "")[:_MAX_USER_CHARS]
        reply_text = (turn.assistant_reply or "")[:_MAX_REPLY_CHARS]
        block = f"Turn {index}:\nUser message:\n{user_text}\n\nAssistant reply:\n{reply_text}"
        if turn.meta_summary:
            block += f"\n\nRun metadata summary:\n{turn.meta_summary[:500]}"
        blocks.append(block)
    joined = "\n\n---\n\n".join(blocks)
    return f"Existing lesson titles:\n{listed}\n\nConversation turns:\n\n{joined}"


def _accept_candidates(
    raw: object, turns: list[ExperienceTurn], existing_titles: list[str] | None
) -> list[ExtractedCandidate]:
    if not isinstance(raw, list):
        return []
    blocked = _existing_title_keys(existing_titles)
    accepted: list[ExtractedCandidate] = []
    for item in raw:
        if not isinstance(item, _CandidateDraft):
            continue
        if not 1 <= item.turn_index <= len(turns):
            continue
        title = _clean_text(item.title, _MAX_TITLE_CHARS)
        lesson = _clean_text(item.lesson, _MAX_LESSON_CHARS)
        if not title or not lesson:
            continue
        key = _title_key(title)
        if key in blocked:
            continue
        blocked.add(key)
        accepted.append(
            ExtractedCandidate(
                source_turn_id=turns[item.turn_index - 1].turn_id,
                title=title,
                symptom=_clean_optional(item.symptom, _MAX_SYMPTOM_CHARS),
                lesson=lesson,
                evidence=_clean_optional(item.evidence, _MAX_EVIDENCE_CHARS),
            )
        )
        if len(accepted) == _MAX_CANDIDATES:
            break
    return accepted


async def extract_experience_lessons(
    model: Model,
    *,
    turns: list[ExperienceTurn],
    existing_titles: list[str] | None,
) -> ExperienceExtraction:
    """对一批候选轮跑一次提炼。无可提炼时回空候选；模型失败抛 ExperienceExtractionUnavailable。"""
    if not turns:
        return ExperienceExtraction()
    try:
        with anyio.fail_after(_TIMEOUT_SECONDS):
            agent = Agent(model, system_prompt=_SYSTEM_PROMPT, output_type=list[_CandidateDraft])
            result = await agent.run(_user_prompt(turns, existing_titles))
    except Exception as exc:
        raise ExperienceExtractionUnavailable(f"experience extraction failed: {exc}") from exc
    usage = result.usage
    return ExperienceExtraction(
        candidates=_accept_candidates(result.output, turns, existing_titles),
        usage={
            "requests": usage.requests,
            "toolCalls": usage.tool_calls,
            "inputTokens": usage.input_tokens,
            "outputTokens": usage.output_tokens,
        },
    )
