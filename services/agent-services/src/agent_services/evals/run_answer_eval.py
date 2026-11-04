"""离线答案质量评估。

用法（在 agent-services 目录）::

    uv run python -m agent_services.evals.run_answer_eval
    uv run python -m agent_services.evals.run_answer_eval --out evals/reports
    uv run python -m agent_services.evals.run_answer_eval --cases evals/skill_qa.jsonl
    uv run python -m agent_services.evals.run_answer_eval --skills orchestration \
        --harness-dir /path/to/harness-variant --lessons-file lessons.json

读取进程 Settings 的 AI_MODEL 与凭证。未配置模型时打印 BLOCKED 并以状态码 2 退出，
不用假模型冒充跑分。核心函数 ``run_answer_eval`` 接受注入的模型，供 CI smoke 使用。

注入接通（#187 UoW-1）：``--cases`` 可跑任意题集（skill_qa 等同风格文件）；
``--skills`` / ``--harness-dir`` / ``--lessons-file`` 分别把 skill 集、平台文件
目录、经验条目按生产组装口径注入每一轮 run_chat，缺省时行为与未接通前一致。
skill 题的触发/完成判分口径见 ``load_skill_cases`` 与 evals/README.md。
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import math
import sys
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from pydantic import ValidationError
from pydantic_ai.models import Model

from agent_services.agent.knowledge_tools import (
    KnowledgeSearchCollector,
    build_knowledge_toolset,
)
from agent_services.agent.model_factory import create_model
from agent_services.agent.runtime import ChatBlocked, ChatReply, run_chat
from agent_services.agent.skills import (
    SkillResolution,
    default_skills_directory,
    resolve_skills,
)
from agent_services.config import RETRIEVAL_MIN_SCORE_DEFAULT, Settings
from agent_services.evals.scoring import METRIC_NAMES, PASS_RULE, aggregate, score_case
from agent_services.retrieval.service import RetrievalService, query_terms
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import ExperienceLessonItem

EVAL_SITE_ID = "eval-golden"
BLOCKED_EXIT = 2
_EMBED_DIM = 2048


@dataclass(frozen=True)
class CorpusDoc:
    source_id: str
    title: str
    content: str


@dataclass(frozen=True)
class GoldenCase:
    id: str
    question: str
    expected_sources: tuple[str, ...]
    expected_keywords: tuple[str, ...]
    category: str
    # 集合级用例（#180 UoW-2）的可选标注：期望行为（枚举→逐份同口径抽取→比较）
    # 与期望答案（含派生数值，如合计）。旧用例无此二字段，默认空串，评分不变。
    expected_behavior: str = ""
    expected_answer: str = ""
    # skill 题集（skill_qa.jsonl）字段：目标 skill、题型（positive/negative/
    # quality）与同锚的黄金集用例 id。黄金集用例三者皆空，行为不变。
    skill: str = ""
    kind: str = ""
    golden_ref: str = ""


@dataclass(frozen=True)
class AnswerEvalRun:
    report: dict[str, object]
    json_path: Path | None = None
    markdown_path: Path | None = None


def evals_root() -> Path:
    """源码树里的 ``services/agent-services/evals``。"""
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "evals" / "golden_qa.jsonl"
        if candidate.is_file():
            return candidate.parent
    raise FileNotFoundError("evals/golden_qa.jsonl was not found from the eval package.")


def _text(payload: dict[str, object], key: str, label: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} is missing {key}.")
    return value.strip()


def _str_list(payload: dict[str, object], key: str, label: str) -> tuple[str, ...]:
    value = payload.get(key)
    if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
        raise ValueError(f"{label} field {key} must be a list of strings.")
    return tuple(item.strip() for item in value)


def load_distractors(path: Path | None = None) -> list[CorpusDoc]:
    """对抗性近失文档层（evals/distractors.json）：与黄金集同行但从不进确定性 CI 索引。

    这些文档刻意与黄金集的问题近失（错型号的同类手册、相邻主题的指南、
    共享「时间/费用」措辞的其他业务），只在真模型 eval（--distractors）时并入
    索引，给检索与引用精度施加真实压力；黄金集病例的 expected_sources 不引用它们。
    """
    return load_corpus(path or evals_root() / "distractors.json")


def load_corpus(path: Path | None = None) -> list[CorpusDoc]:
    raw = json.loads((path or evals_root() / "corpus.json").read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("corpus.json must be a list.")
    docs: list[CorpusDoc] = []
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            raise ValueError(f"corpus item {index} must be an object.")
        docs.append(
            CorpusDoc(
                source_id=_text(item, "source_id", f"corpus[{index}]"),
                title=_text(item, "title", f"corpus[{index}]"),
                content=_text(item, "content", f"corpus[{index}]"),
            )
        )
    return docs


def _opt_text(payload: dict[str, object], key: str, label: str) -> str:
    """可选文本字段：缺省为空串；出现时必须是非空字符串。"""
    value = payload.get(key)
    if value is None:
        return ""
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} field {key} must be a non-empty string when present.")
    return value.strip()


def load_cases(path: Path | None = None) -> list[GoldenCase]:
    cases: list[GoldenCase] = []
    text = (path or evals_root() / "golden_qa.jsonl").read_text(encoding="utf-8")
    for line_number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        item = json.loads(line)
        if not isinstance(item, dict):
            raise ValueError(f"golden line {line_number} must be an object.")
        label = f"line {line_number}"
        cases.append(
            GoldenCase(
                id=_text(item, "id", label),
                question=_text(item, "question", label),
                expected_sources=_str_list(item, "expected_sources", label),
                expected_keywords=_str_list(item, "expected_keywords", label),
                category=_text(item, "category", label),
                expected_behavior=_opt_text(item, "expected_behavior", label),
                expected_answer=_opt_text(item, "expected_answer", label),
            )
        )
    return cases


# skill 题集里既无自身锚点字段、又无 golden_ref 的题面归入此类别：只作报告
# 分组标签，不触发拒答逻辑，也无机器可判的来源/关键词（行为题，见下）。
SKILL_FALLBACK_CATEGORY = "skill"


def _opt_str_list(
    payload: dict[str, object], key: str, label: str
) -> tuple[str, ...] | None:
    """可选字符串列表字段：字段缺席返回 None（由调用方决定派生来源）。"""
    if key not in payload:
        return None
    return _str_list(payload, key, label)


def _blankable_text(payload: dict[str, object], key: str, label: str) -> str:
    """skill 题面的可选文本：缺席或显式空串都记空串（行为题的 expected_answer 合法为空）。"""
    value = payload.get(key)
    if value is None:
        return ""
    if not isinstance(value, str):
        raise ValueError(f"{label} field {key} must be a string when present.")
    return value.strip()


def load_skill_cases(
    path: Path | None = None, *, golden_cases: Sequence[GoldenCase] | None = None
) -> list[GoldenCase]:
    """加载 skill_qa.jsonl 形状的题集，并把评分锚点派生到 GoldenCase 上。

    skill 题面本身只有 id/skill/kind/question/expected_behavior/expected_answer，
    评分锚点（category / expected_sources / expected_keywords）按「同锚」派生：

    - 题面自带对应字段时以题面为准（显式空列表即空，不回退）；
    - 否则取 ``golden_ref`` 指向的黄金集用例的同名字段；
    - 再否则：category 记 ``SKILL_FALLBACK_CATEGORY``、来源与关键词皆空——
      这类题是纯行为题，四指标全为 None、passed 恒 False（口径见 scoring
      与 evals/README.md），触发/完成只看 triggered 与人工核对 expected_behavior。

    判分口径（#187 UoW-1 定死）：triggered＝该题 skill 出现在本轮 run 的
    skillsApplied（即本轮 resolve_skills 的 applied 集，与生产响应同源）；
    completed＝triggered 且 score_case 的 passed 为真。
    """
    golden_by_id = {
        case.id: case for case in (golden_cases if golden_cases is not None else load_cases())
    }
    cases: list[GoldenCase] = []
    text = (path or evals_root() / "skill_qa.jsonl").read_text(encoding="utf-8")
    for line_number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        item = json.loads(line)
        if not isinstance(item, dict):
            raise ValueError(f"skill line {line_number} must be an object.")
        label = f"skill line {line_number}"
        ref = _blankable_text(item, "golden_ref", label)
        anchor = golden_by_id.get(ref) if ref else None
        if ref and anchor is None:
            raise ValueError(f"{label} references unknown golden case {ref}.")
        category = _blankable_text(item, "category", label) or (
            anchor.category if anchor is not None else SKILL_FALLBACK_CATEGORY
        )
        sources = _opt_str_list(item, "expected_sources", label)
        if sources is None:
            sources = anchor.expected_sources if anchor is not None else ()
        keywords = _opt_str_list(item, "expected_keywords", label)
        if keywords is None:
            keywords = anchor.expected_keywords if anchor is not None else ()
        cases.append(
            GoldenCase(
                id=_text(item, "id", label),
                question=_text(item, "question", label),
                expected_sources=sources,
                expected_keywords=keywords,
                category=category,
                expected_behavior=_blankable_text(item, "expected_behavior", label),
                expected_answer=_blankable_text(item, "expected_answer", label),
                skill=_text(item, "skill", label),
                kind=_blankable_text(item, "kind", label),
                golden_ref=ref,
            )
        )
    return cases


def load_any_cases(path: Path) -> list[GoldenCase]:
    """按内容分派加载任意题集：任一行带 skill 字段即按 skill 题集解析。"""
    text = path.read_text(encoding="utf-8")
    for line in text.splitlines():
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError:
            continue  # 坏行留给正式加载器报错，这里只做形状嗅探。
        if isinstance(item, dict) and "skill" in item:
            return load_skill_cases(path)
    return load_cases(path)


def validate_eval_cases(corpus: Sequence[CorpusDoc], cases: Sequence[GoldenCase]) -> None:
    """非黄金题集（如 skill_qa）的宽容校验。

    只守诚实性底线：id 不重复、期望来源必须真实存在于语料、不可答题不得
    携带来源/关键词锚点。不要求可答题来源与关键词俱全——skill 题集里
    行为题合法地两样都没有（判分口径见 load_skill_cases）。
    """
    known_set = {doc.source_id for doc in corpus}
    seen: set[str] = set()
    for case in cases:
        if case.id in seen:
            raise ValueError(f"duplicate case id {case.id}.")
        seen.add(case.id)
        unknown = [source for source in case.expected_sources if source not in known_set]
        if unknown:
            raise ValueError(f"{case.id} references unknown sources {unknown}.")
        if case.category == "unanswerable" and (case.expected_sources or case.expected_keywords):
            raise ValueError(f"{case.id} is unanswerable and must not list sources or keywords.")


def load_lessons_file(path: Path) -> list[ExperienceLessonItem]:
    """加载 --lessons-file 的经验条目 JSON（形状与 ChatRequest.experienceLessons 一致）。

    文件缺失、JSON 坏、形状不对一律抛 ValueError 明确报错，绝不静默当无条目；
    空数组是合法的「关」臂（显式零条目）。
    """
    try:
        raw_text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ValueError(f"--lessons-file cannot be read: {path} ({exc}).") from exc
    try:
        raw = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"--lessons-file is not valid JSON: {path} ({exc}).") from exc
    if not isinstance(raw, list):
        raise ValueError("--lessons-file must be a JSON array of {lessonNo, lesson} objects.")
    lessons: list[ExperienceLessonItem] = []
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            raise ValueError(f"--lessons-file item {index} must be an object.")
        try:
            lessons.append(ExperienceLessonItem.model_validate(item))
        except ValidationError as exc:
            raise ValueError(f"--lessons-file item {index} is invalid: {exc}.") from exc
    return lessons


def validate_golden(corpus: Sequence[CorpusDoc], cases: Sequence[GoldenCase]) -> None:
    known = [doc.source_id for doc in corpus]
    if len(known) != len(set(known)):
        raise ValueError("corpus has a duplicate source_id.")
    known_set = set(known)
    seen: set[str] = set()
    for case in cases:
        if case.id in seen:
            raise ValueError(f"duplicate case id {case.id}.")
        seen.add(case.id)
        unknown = [source for source in case.expected_sources if source not in known_set]
        if unknown:
            raise ValueError(f"{case.id} references unknown sources {unknown}.")
        unanswerable = case.category == "unanswerable"
        if unanswerable and (case.expected_sources or case.expected_keywords):
            raise ValueError(f"{case.id} is unanswerable and must not list sources or keywords.")
        if not unanswerable and (not case.expected_sources or not case.expected_keywords):
            raise ValueError(f"{case.id} needs expected sources and keywords.")
        if case.category == "collection" and not case.expected_behavior:
            raise ValueError(f"{case.id} is a collection case and must state expected_behavior.")


class DeterministicEmbedder:
    """与测试 FakeEmbedder 同一组方法，但是按 query_terms 做哈希投影。

    FakeEmbedder 对所有文本返回同一个向量，余弦分全是 1，无关问题也会超过
    检索门槛并带上 citations。哈希向量仍是确定性的、不访问网络，无关文本的
    余弦分落在门槛以下，词法通道继续走 RetrievalService 自己的实现。
    """

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [_embed_one(text) for text in texts]

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return await self.embed(texts)

    async def embed_query(self, text: str) -> list[float]:
        vectors = await self.embed([text])
        if len(vectors) != 1:
            raise ValueError(f"Embedding response count {len(vectors)} does not match input count 1.")
        return vectors[0]


def _embed_one(text: str) -> list[float]:
    acc = [0.0] * _EMBED_DIM
    for token in query_terms(text):
        digest = hashlib.sha256(token.casefold().encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:2], "big") % _EMBED_DIM
        sign = 1.0 if digest[2] & 1 else -1.0
        weight = 3.0 if any(mark in token for mark in "-.") else 1.0
        acc[bucket] += sign * weight
    norm = math.sqrt(sum(value * value for value in acc))
    if norm == 0.0:
        return acc
    return [value / norm for value in acc]


def _cosine(left: Sequence[float], right: Sequence[float]) -> float:
    if len(left) != len(right) or not left:
        return 0.0
    return sum(a * b for a, b in zip(left, right, strict=True))


@dataclass
class _Row:
    agent_id: str
    source_id: str
    chunk_index: int
    content: str
    embedding: list[float]
    embedding_model: str = ""


class MemoryRetrievalStore:
    """进程内索引。向量按余弦排序，词法按字面量子串命中数排序，都强制 agent_id。"""

    def __init__(self) -> None:
        self._rows: list[_Row] = []
        self._titles: dict[tuple[str, str], str] = {}
        self._dimensions: int | None = None

    async def upsert_document(
        self,
        *,
        agent_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
        embedding_model: str = "",
    ) -> int:
        self._rows = [
            row
            for row in self._rows
            if not (
                row.agent_id == agent_id
                and row.source_id == source_id
                and row.embedding_model == embedding_model
            )
        ]
        self._titles[(agent_id, source_id)] = title
        for index, (content, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
            self._rows.append(
                _Row(
                    agent_id=agent_id,
                    source_id=source_id,
                    chunk_index=index,
                    content=content,
                    embedding=list(embedding),
                    embedding_model=embedding_model,
                )
            )
            self._dimensions = len(embedding)
        return len(chunks)

    async def delete_document(self, *, agent_id: str, source_id: str) -> None:
        self._rows = [
            row for row in self._rows if not (row.agent_id == agent_id and row.source_id == source_id)
        ]
        self._titles.pop((agent_id, source_id), None)

    async def search(
        self,
        *,
        agent_id: str,
        embedding: list[float],
        top_k: int,
        embedding_model: str = "",
    ) -> list[RetrievedChunk]:
        if top_k < 1:
            return []
        scored = [
            (_cosine(row.embedding, embedding), row)
            for row in self._rows
            if row.agent_id == agent_id and row.embedding_model == embedding_model
        ]
        scored.sort(key=lambda item: (-item[0], item[1].source_id, item[1].chunk_index))
        return [
            RetrievedChunk(
                source_id=row.source_id,
                chunk_index=row.chunk_index,
                content=row.content,
                score=score,
            )
            for score, row in scored[:top_k]
        ]

    async def search_lexical(
        self,
        *,
        agent_id: str,
        tokens: list[str],
        limit: int,
        embedding_models: list[str] | None = None,
    ) -> list[RetrievedChunk]:
        if not tokens or limit < 1:
            return []
        allowed = None if embedding_models is None else set(embedding_models)
        ranked: list[tuple[int, _Row]] = []
        for row in self._rows:
            if row.agent_id != agent_id:
                continue
            if allowed is not None and row.embedding_model not in allowed:
                continue
            folded = row.content.casefold()
            hits = sum(1 for token in tokens if token.casefold() in folded)
            if hits <= 0:
                continue
            ranked.append((hits, row))
        ranked.sort(key=lambda item: (-item[0], item[1].source_id, item[1].chunk_index))
        return [
            RetrievedChunk(
                source_id=row.source_id,
                chunk_index=row.chunk_index,
                content=row.content,
                score=0.0,
            )
            for _, row in ranked[:limit]
        ]

    async def current_dimensions(self) -> int | None:
        return self._dimensions

    async def ensure_schema(self, dimensions: int) -> None:
        self._dimensions = dimensions

    async def recreate_schema(self, new_dimensions: int) -> None:
        self._rows.clear()
        self._titles.clear()
        self._dimensions = new_dimensions

    async def close(self) -> None:
        return None


async def build_eval_index(
    corpus: Sequence[CorpusDoc], *, agent_id: str = EVAL_SITE_ID
) -> RetrievalService:
    """种入语料。重排关掉：评估模型只负责作答，不额外改检索顺序。"""
    service = RetrievalService(
        DeterministicEmbedder(),
        MemoryRetrievalStore(),
        min_score=RETRIEVAL_MIN_SCORE_DEFAULT,
        rerank_enabled=False,
    )
    for doc in corpus:
        await service.upsert_document(
            agent_id=agent_id,
            source_id=doc.source_id,
            title=doc.title,
            content=doc.content,
        )
    return service


def _jsonable_citations(
    citations: list[dict[str, object]] | None,
) -> list[dict[str, object]] | None:
    if not citations:
        return None
    cleaned: list[dict[str, object]] = []
    for item in citations:
        copied = dict(item)
        score = copied.get("score")
        if isinstance(score, float):
            copied["score"] = round(score, 6)
        cleaned.append(copied)
    return cleaned


def _citation_ids(citations: list[dict[str, object]] | None) -> list[str] | None:
    if not citations:
        return None
    found: list[str] = []
    for item in citations:
        source_id = item.get("sourceId")
        if isinstance(source_id, str) and source_id and source_id not in found:
            found.append(source_id)
    return found or None


def _observed_sources(
    chunks: Sequence[RetrievedChunk], citation_source_ids: Sequence[str] | None
) -> list[str]:
    found: list[str] = []
    for chunk in chunks:
        if chunk.source_id and chunk.source_id not in found:
            found.append(chunk.source_id)
    for source_id in citation_source_ids or []:
        if source_id not in found:
            found.append(source_id)
    return found


async def _run_case(
    service: RetrievalService,
    model: Model,
    case: GoldenCase,
    *,
    agent_id: str,
    skill_resolution: SkillResolution | None = None,
    skills_max_instruction_chars: int | None = None,
    harness_dir: str | None = None,
    experience_lessons: list[ExperienceLessonItem] | None = None,
) -> dict[str, object]:
    # 与生产路径一致：知识检索由 agent 经 search_site_knowledge 工具执行，
    # 命中片段经 collector 回流生成 citations；评测量的就是这条链。
    # skills / harness_dir / experience_lessons 同样按生产口径透传 run_chat：
    # skill_resolution 由调用方经 agent.skills.resolve_skills 解析（与
    # api/internal.py 同一函数、同一输入），不在 runner 内另造旁路。
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset(agent_id, service, collector=collector)
    outcome = await run_chat(
        model,
        agent_id=agent_id,
        message=case.question,
        toolsets=[toolset],
        knowledge_collector=collector,
        memory_extraction_enabled=False,
        skill_instructions=skill_resolution.instructions if skill_resolution else None,
        skill_ids=list(skill_resolution.applied_ids) if skill_resolution else None,
        skills_missing=list(skill_resolution.missing_ids) if skill_resolution else None,
        skills_max_instruction_chars=skills_max_instruction_chars,
        harness_dir=harness_dir,
        experience_lessons=experience_lessons,
    )
    chunks = collector.chunks
    degraded = collector.degraded
    if isinstance(outcome, ChatReply):
        answer = outcome.reply
        citations = outcome.citations
        blocked_reason = None
        error_code = None
    elif isinstance(outcome, ChatBlocked):
        answer = ""
        citations = None
        blocked_reason = outcome.message
        error_code = outcome.error_code
    else:
        answer = ""
        citations = None
        blocked_reason = "AI provider returned no reply."
        error_code = "EXTERNAL_DEPENDENCY_BLOCKED"
    citations = _jsonable_citations(citations)
    citation_source_ids = _citation_ids(citations)
    observed = _observed_sources(chunks, citation_source_ids)
    scores = score_case(
        category=case.category,
        expected_sources=case.expected_sources,
        expected_keywords=case.expected_keywords,
        observed_sources=observed,
        citation_source_ids=citation_source_ids,
        answer=answer,
    )
    row: dict[str, object] = {
        "id": case.id,
        "category": case.category,
        "question": case.question,
        "expectedSources": list(case.expected_sources),
        "reply": answer,
        "blockedReason": blocked_reason,
        "errorCode": error_code,
        "retrievalDegraded": degraded,
        "retrieved": [
            {
                "sourceId": chunk.source_id,
                "chunkIndex": chunk.chunk_index,
                "score": round(chunk.score, 6),
            }
            for chunk in chunks
        ],
        "citations": citations,
        "scores": scores,
    }
    # 集合级标注只在用例带了才进报告，旧用例的报告形状不变。
    if case.expected_behavior:
        row["expectedBehavior"] = case.expected_behavior
    if case.expected_answer:
        row["expectedAnswer"] = case.expected_answer
    # skill 题的触发/完成标注只在题面带 skill 字段时出现，黄金题行形状不变。
    # skillsApplied 即本轮解析的 applied 集，与生产响应 skillsApplied 同源。
    if case.skill:
        applied = list(skill_resolution.applied_ids) if skill_resolution else []
        triggered = case.skill in applied
        row["skill"] = case.skill
        row["kind"] = case.kind
        row["skillsApplied"] = applied
        if skill_resolution and skill_resolution.missing_ids:
            row["skillsMissing"] = list(skill_resolution.missing_ids)
        row["triggered"] = triggered
        row["completed"] = bool(triggered and scores["passed"])
    return row


def _stamp(when: datetime) -> datetime:
    if when.tzinfo is None:
        return when.replace(tzinfo=UTC)
    return when.astimezone(UTC)


def output_paths(out: Path, when: datetime) -> tuple[Path, Path]:
    stamped = _stamp(when).strftime("%Y%m%dT%H%M%SZ")
    if (out.exists() and out.is_dir()) or out.suffix == "":
        out.mkdir(parents=True, exist_ok=True)
        stem = out / f"answer-eval-{stamped}"
        return stem.with_suffix(".json"), stem.with_suffix(".md")
    if out.suffix.lower() not in {".json", ".md"}:
        raise ValueError("--out must be a directory or a .json/.md path.")
    out.parent.mkdir(parents=True, exist_ok=True)
    return out.with_suffix(".json"), out.with_suffix(".md")


def _cell(value: object) -> str:
    if value is None:
        return "n/a"
    if isinstance(value, bool):
        return "yes" if value else "no"
    if isinstance(value, float):
        return f"{value:.3f}"
    if isinstance(value, int):
        return str(value)
    return str(value).replace("|", "\\|").replace("\n", " ")


def render_markdown(report: dict[str, object]) -> str:
    summary = report["summary"]
    if not isinstance(summary, dict):
        raise ValueError("report summary is missing.")
    overall = summary["overall"]
    by_category = summary["byCategory"]
    if not isinstance(overall, dict) or not isinstance(by_category, dict):
        raise ValueError("report summary is missing tables.")
    lines = [
        "# Answer quality eval",
        "",
        f"- Model: `{report['model']}`",
        f"- Generated: {report['generatedAt']}",
        f"- Pass 判准：{report['passRule']}",
    ]
    # 注入标注与 JSON 同口径：只在实际启用时出现（#187 UoW-1）。
    skills_value = report.get("skills")
    if isinstance(skills_value, list) and skills_value:
        lines.append(f"- Skills: `{', '.join(str(item) for item in skills_value)}`")
    if report.get("harnessDir"):
        lines.append(f"- Harness dir: `{report['harnessDir']}`")
    if report.get("lessonsCount"):
        lines.append(f"- Experience lessons: {report['lessonsCount']} 条（文件注入）")
    lines.extend(
        [
            "",
            "没有 citations 的 citation_precision、没有关键词的 keyword_coverage、",
            "非不可答题的 refusal_correct、没有期望来源的 retrieval_hit 记 n/a，不进均值。",
            "",
            "## Overall",
            "",
            "| metric | mean |",
            "| --- | --- |",
        ]
    )
    for name in (*METRIC_NAMES, "count"):
        lines.append(f"| {name} | {_cell(overall.get(name))} |")
    lines.extend(
        [
            "",
            "## By category",
            "",
            "| category | n | retrieval_hit | citation_precision | keyword_coverage | refusal_correct | passed |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
    )
    for category, block in by_category.items():
        if not isinstance(block, dict):
            continue
        lines.append(
            "| "
            + " | ".join(
                [
                    _cell(category),
                    _cell(block.get("count")),
                    _cell(block.get("retrieval_hit")),
                    _cell(block.get("citation_precision")),
                    _cell(block.get("keyword_coverage")),
                    _cell(block.get("refusal_correct")),
                    _cell(block.get("passed")),
                ]
            )
            + " |"
        )
    cases = report["cases"]
    case_rows = (
        [case for case in cases if isinstance(case, dict)] if isinstance(cases, list) else []
    )
    # skill 题集才有 triggered/completed 两列；黄金集报告表格形状不变。
    has_skill_rows = any("triggered" in case for case in case_rows)
    if has_skill_rows:
        lines.extend(
            [
                "",
                "## Cases",
                "",
                "| id | category | skill | kind | triggered | completed | passed | retrieval_hit | citation_precision | keyword_coverage | refusal_correct |",
                "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
            ]
        )
    else:
        lines.extend(
            [
                "",
                "## Cases",
                "",
                "| id | category | passed | retrieval_hit | citation_precision | keyword_coverage | refusal_correct |",
                "| --- | --- | --- | --- | --- | --- | --- |",
            ]
        )
    for case in case_rows:
        scores = case.get("scores")
        if not isinstance(scores, dict):
            scores = {}
        cells = [
            _cell(case.get("id")),
            _cell(case.get("category")),
        ]
        if has_skill_rows:
            cells.extend(
                [
                    _cell(case.get("skill")),
                    _cell(case.get("kind")),
                    _cell(case.get("triggered")),
                    _cell(case.get("completed")),
                ]
            )
        cells.extend(
            [
                _cell(scores.get("passed")),
                _cell(scores.get("retrieval_hit")),
                _cell(scores.get("citation_precision")),
                _cell(scores.get("keyword_coverage")),
                _cell(scores.get("refusal_correct")),
            ]
        )
        lines.append("| " + " | ".join(cells) + " |")
    lines.append("")
    return "\n".join(lines)


async def run_answer_eval(
    model: Model,
    *,
    model_name: str,
    corpus: Sequence[CorpusDoc] | None = None,
    cases: Sequence[GoldenCase] | None = None,
    out: Path | None = None,
    generated_at: datetime | None = None,
    agent_id: str = EVAL_SITE_ID,
    skills: Sequence[str] | None = None,
    harness_dir: str | None = None,
    experience_lessons: Sequence[ExperienceLessonItem] | None = None,
    skills_dir: str | None = None,
    skills_max_instruction_chars: int | None = None,
) -> AnswerEvalRun:
    """对题集跑检索 + run_chat，评分，并在给出 out 时写下 JSON 与 Markdown。

    注入参数（#187 UoW-1；全部缺省时行为与接通前逐字一致）：

    - ``skills``：本轮基础 skill 集（CLI ``--skills``）。带 skill 字段的题
      （skill_qa）在此基础上再追加该题自己的 skill——与 #182 人工口径
      「每题显式带该题 skill」一致；解析走生产同一函数 resolve_skills，
      未知 id 按生产缺省 ignore 口径记入 missing，不中断整轮。
    - ``harness_dir``：平台文件目录（CLI ``--harness-dir``），语义等价
      生产 AGENT_HARNESS_DIR；缺省 None 走源码树默认目录。
    - ``experience_lessons``：经验条目（CLI ``--lessons-file`` 已解析）；
      缺省 None 即无条目（对照的「关」臂）。
    - ``skills_dir`` / ``skills_max_instruction_chars``：CLI 层从进程
      Settings 传入（与生产同源）；函数层缺省分别取平台默认目录与不设限。
    """
    loaded_corpus = list(corpus) if corpus is not None else load_corpus()
    loaded_cases = list(cases) if cases is not None else load_cases()
    if any(case.skill for case in loaded_cases):
        validate_eval_cases(loaded_corpus, loaded_cases)
    else:
        validate_golden(loaded_corpus, loaded_cases)
    when = _stamp(generated_at or datetime.now(UTC))
    service = await build_eval_index(loaded_corpus, agent_id=agent_id)
    base_skills = [skill_id.strip() for skill_id in skills or [] if skill_id.strip()]
    resolved_skills_dir = skills_dir or default_skills_directory()
    resolutions: dict[tuple[str, ...], SkillResolution] = {}

    def resolution_for(case: GoldenCase) -> SkillResolution | None:
        requested = list(base_skills)
        if case.skill and case.skill not in requested:
            requested.append(case.skill)
        if not requested:
            return None
        key = tuple(requested)
        if key not in resolutions:
            resolutions[key] = resolve_skills(
                skills_dir=resolved_skills_dir,
                requested_skill_ids=requested,
                default_skill_ids=None,
            )
        return resolutions[key]

    lessons = list(experience_lessons) if experience_lessons else None
    rows: list[dict[str, object]] = []
    try:
        for case in loaded_cases:
            rows.append(
                await _run_case(
                    service,
                    model,
                    case,
                    agent_id=agent_id,
                    skill_resolution=resolution_for(case),
                    skills_max_instruction_chars=skills_max_instruction_chars,
                    harness_dir=harness_dir,
                    experience_lessons=lessons,
                )
            )
    finally:
        await service.close()
    summary = aggregate(
        [{"category": row["category"], **row["scores"]} for row in rows if isinstance(row["scores"], dict)]
    )
    report: dict[str, object] = {
        "model": model_name,
        "generatedAt": when.isoformat(),
        "passRule": PASS_RULE,
        "summary": summary,
        "cases": rows,
    }
    # 本轮注入标注：只在实际启用了对应注入时出现，缺省报告形状不变。
    # UoW-2 的配置指纹从这三个键与 model/passRule 取数扩展。
    if base_skills:
        report["skills"] = base_skills
    if harness_dir:
        report["harnessDir"] = harness_dir
    if lessons:
        report["lessonsCount"] = len(lessons)
    json_path = None
    markdown_path = None
    if out is not None:
        json_path, markdown_path = output_paths(out, when)
        json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        markdown_path.write_text(render_markdown(report), encoding="utf-8")
    return AnswerEvalRun(report=report, json_path=json_path, markdown_path=markdown_path)


def blocked_text(reason: str) -> str:
    return (
        "BLOCKED: answer quality eval did not run. "
        f"{reason} "
        "Refusing to score with a fake model."
    )


def configured_model_block(settings: Settings) -> tuple[Model | None, str, str]:
    """返回 (model, spec, blocked_text)。blocked_text 非空时调用方不得跑分。"""
    if settings.agent_contract_use_test_model:
        return (
            None,
            "blocked",
            blocked_text(
                "AGENT_CONTRACT_USE_TEST_MODEL is a contract-test fake and cannot stand in for a scored run."
            ),
        )
    if not settings.ai_model.strip():
        return None, "blocked", blocked_text("AI model is not configured (missing AI_MODEL).")
    handle = create_model(settings)
    if handle.model is None or not handle.live:
        return (
            None,
            handle.spec,
            blocked_text(handle.blocked_reason or "AI provider is not configured."),
        )
    return handle.model, handle.spec, ""


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run the offline answer-quality eval against the configured AI model."
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=None,
        help="Directory or .json/.md path. Default: evals/reports/answer-eval-<utc>.json and .md",
    )
    parser.add_argument(
        "--distractors",
        action="store_true",
        help="Also index evals/distractors.json (adversarial near-miss documents) alongside the golden corpus.",
    )
    parser.add_argument(
        "--cases",
        type=Path,
        default=None,
        help="Case file (.jsonl): golden_qa shape or skill_qa shape. Default: evals/golden_qa.jsonl.",
    )
    parser.add_argument(
        "--harness-dir",
        type=Path,
        default=None,
        help="Platform harness files directory (background/guardrails/tool_guidance), "
        "equivalent to the production AGENT_HARNESS_DIR. Default: the source tree harness/.",
    )
    parser.add_argument(
        "--skills",
        default=None,
        help="Comma-separated skill ids injected for every case, resolved exactly like "
        "production (agent.skills.resolve_skills). Skill cases additionally request their "
        "own case skill. Default: no skills.",
    )
    parser.add_argument(
        "--lessons-file",
        type=Path,
        default=None,
        help="JSON array of experience lessons ([{lessonNo, lesson}]) injected into every "
        "run, same shape as the chat request field. A missing or malformed file is an "
        "error, never silently treated as no lessons.",
    )
    return parser


def _parse_skills_arg(raw: str | None) -> list[str]:
    if not raw:
        return []
    return [part.strip() for part in raw.split(",") if part.strip()]


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    # 注入类文件先验：坏文件要在跑分前明确报错，不许静默降级成缺省行为。
    cases: list[GoldenCase] | None = None
    if args.cases is not None:
        try:
            cases = load_any_cases(args.cases)
        except (OSError, ValueError) as exc:
            print(f"ERROR: --cases could not be loaded: {exc}", file=sys.stderr, flush=True)
            return BLOCKED_EXIT
    lessons: list[ExperienceLessonItem] | None = None
    if args.lessons_file is not None:
        try:
            lessons = load_lessons_file(args.lessons_file)
        except ValueError as exc:
            print(f"ERROR: {exc}", file=sys.stderr, flush=True)
            return BLOCKED_EXIT
    harness_dir: str | None = None
    if args.harness_dir is not None:
        if not args.harness_dir.is_dir():
            print(
                f"ERROR: --harness-dir is not a directory: {args.harness_dir}",
                file=sys.stderr,
                flush=True,
            )
            return BLOCKED_EXIT
        harness_dir = str(args.harness_dir)
    settings = Settings()
    model, spec, blocked = configured_model_block(settings)
    if blocked or model is None:
        print(blocked or blocked_text("AI model is not configured (missing AI_MODEL)."), flush=True)
        return BLOCKED_EXIT
    destination = args.out if args.out is not None else evals_root() / "reports"
    corpus = load_corpus() + load_distractors() if args.distractors else None
    result = asyncio.run(
        run_answer_eval(
            model,
            model_name=spec,
            out=destination,
            corpus=corpus,
            cases=cases,
            skills=_parse_skills_arg(args.skills),
            harness_dir=harness_dir,
            experience_lessons=lessons,
            skills_dir=settings.agent_skills_dir.strip() or default_skills_directory(),
            skills_max_instruction_chars=settings.agent_skills_max_instruction_chars,
        )
    )
    if result.json_path is not None:
        print(result.json_path, flush=True)
    if result.markdown_path is not None:
        print(result.markdown_path, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
