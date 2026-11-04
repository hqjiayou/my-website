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

对照与评分完整性（#187 UoW-2）：每份报告带配置指纹（``fingerprint``）；
``--baseline <report.json>`` 逐题 diff（pass 翻转、指标变化、单边题），
相对基线出现 pass→fail 翻转时退出码 3；单题运行异常/超时记 unscored
（不记 0 分、不进均值、整轮全 unscored 时按 BLOCKED 以退出码 2 处理）。
指纹与 unscored 口径见 ``build_fingerprint`` / ``_unscored_row`` 与
evals/README.md。

行为判分与题集扩容（#190）：题目级可选 ``behavior_checks`` 由 scoring
逐项确定性判定、计入 pass，逐项结果附在报告行 ``behaviorChecks``；
``citation_precision`` 低于阈值记逐题 ``warnings``（不进 pass，汇总单列）；
题目级可选 ``history``（先前轮次）按 run_chat 的 history 入参形状透传，
支撑多轮题；经验敏感题集 ``evals/experience_qa.jsonl`` 配合
``--lessons-file evals/fixtures/lessons_sample.json`` 做关/开两轮对照。
口径见 scoring 与 evals/README.md。
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import logging
import math
import sys
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from pydantic import ValidationError
from pydantic_ai.models import Model

from agent_services.agent.harness import default_harness_directory
from agent_services.agent.knowledge_tools import (
    KnowledgeSearchCollector,
    build_knowledge_toolset,
)
from agent_services.agent.model_factory import create_model
from agent_services.agent.runtime import ChatBlocked, ChatReply, run_chat
from agent_services.agent.skill_tools import (
    SkillLoadCollector,
    build_skill_toolset,
    merge_skills_applied,
)
from agent_services.agent.skills import (
    SkillResolution,
    default_skills_directory,
    list_skill_catalog,
    resolve_skills,
)
from agent_services.config import RETRIEVAL_MIN_SCORE_DEFAULT, Settings
from agent_services.evals.scoring import (
    BEHAVIOR_CHECK_NAMES,
    METRIC_NAMES,
    PASS_RULE,
    aggregate,
    case_warnings,
    evaluate_behavior_checks,
    score_case,
)
from agent_services.retrieval.service import RetrievalService, query_terms
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import ChatHistoryMessage, ExperienceLessonItem

EVAL_SITE_ID = "eval-golden"
BLOCKED_EXIT = 2
REGRESSION_EXIT = 3
_EMBED_DIM = 2048
# 单题墙钟上限（秒）：run_chat 内部只有工具级超时，没有整题上限；单题超过
# 此限记 unscored（见 _unscored_row），不拖垮整轮、也不无限等。真模型单题
# （检索＋多轮工具）量级在分钟内，300 秒是宽松上限而非目标耗时。
CASE_TIMEOUT_SECONDS = 300.0

logger = logging.getLogger(__name__)


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
    # 自选题（Issue #195）：mode == "self-select" 时 runner 不预选追加该题
    # skill，run 中是否经 load_skill 加载全看模型自选；缺省空串＝预选题。
    mode: str = ""
    # 行为判分（#190 UoW-1）：题目级 opt-in 的确定性行为检查名
    # （scoring.BEHAVIOR_CHECK_NAMES 子集），声明了才判、计入 pass。
    behavior_checks: tuple[str, ...] = ()
    # 多轮题（#190 UoW-2）：先前轮次，元素形状即 run_chat 的 history 入参
    # （ChatHistoryMessage：role/content），缺省无 history、行为不变。
    history: tuple[ChatHistoryMessage, ...] = ()


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


def _behavior_check_list(payload: dict[str, object], label: str) -> tuple[str, ...]:
    """可选 behavior_checks 字段：缺席或显式空列表都记空（未声明不判）。"""
    checks = _opt_str_list(payload, "behavior_checks", label)
    return checks if checks is not None else ()


def _history_items(
    payload: dict[str, object], label: str
) -> tuple[ChatHistoryMessage, ...]:
    """可选 history 字段：先前轮次，逐项按 ChatHistoryMessage 校验。

    形状与 run_chat 的 history 入参一致（{role: USER|ASSISTANT, content}）；
    字段出现时必须是非空列表，坏形状在加载期报错，不静默丢弃。
    """
    if "history" not in payload:
        return ()
    value = payload["history"]
    if not isinstance(value, list) or not value:
        raise ValueError(
            f"{label} field history must be a non-empty list when present."
        )
    items: list[ChatHistoryMessage] = []
    for index, entry in enumerate(value):
        if not isinstance(entry, dict):
            raise ValueError(f"{label} history item {index} must be an object.")
        try:
            items.append(ChatHistoryMessage.model_validate(entry))
        except ValidationError as exc:
            raise ValueError(
                f"{label} history item {index} is invalid: {exc}."
            ) from exc
    return tuple(items)


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
                behavior_checks=_behavior_check_list(item, label),
                history=_history_items(item, label),
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

    自选题（#195）：题面带 ``mode: "self-select"`` 时不预选追加题面 skill，
    skillsApplied 读「预选集＋load_skill 自选集」的合并集（与生产响应同一
    merge 口径），triggered 即模型是否自选了该 skill；这类题须在进程 env
    开 AGENT_SKILLS_SELF_SELECT_ENABLED 才跑得出自选行为。
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
                mode=_blankable_text(item, "mode", label),
                behavior_checks=_behavior_check_list(item, label),
                history=_history_items(item, label),
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


def _validate_case_extras(case: GoldenCase) -> None:
    """题目级可选字段的校验（#190）：行为检查名须已知；history 须轮次规整。

    history 是「先前轮次」：本轮 question 是接在其后的用户轮，故 history
    必须以 USER 开头、ASSISTANT 结尾、角色严格交替，否则多轮语义不成立。
    """
    unknown = [
        name for name in case.behavior_checks if name not in BEHAVIOR_CHECK_NAMES
    ]
    if unknown:
        raise ValueError(f"{case.id} declares unknown behavior checks {unknown}.")
    if case.history:
        roles = [item.role for item in case.history]
        alternates = all(
            roles[index] != roles[index + 1] for index in range(len(roles) - 1)
        )
        if roles[0] != "USER" or roles[-1] != "ASSISTANT" or not alternates:
            raise ValueError(
                f"{case.id} history must start with USER, end with ASSISTANT, "
                "and alternate roles."
            )


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
        _validate_case_extras(case)


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


def corpus_fingerprint(corpus: Sequence[CorpusDoc]) -> dict[str, object]:
    """语料的内容标识：文档数＋规范化内容哈希（按 source_id 排序拼接三字段）。

    同一份语料（含 --distractors 并入与否）必然得到同一哈希，语料增删改
    任一字段都会变——这是「分数只与同指纹基线可比」里 corpus 一项的锚点。
    """
    digest = hashlib.sha256()
    for doc in sorted(corpus, key=lambda item: item.source_id):
        digest.update(doc.source_id.encode("utf-8"))
        digest.update(b"\n")
        digest.update(doc.title.encode("utf-8"))
        digest.update(b"\n")
        digest.update(doc.content.encode("utf-8"))
        digest.update(b"\n")
    return {"documents": len(corpus), "sha256": digest.hexdigest()}


def build_fingerprint(
    *,
    model_name: str,
    cases_file: str | None,
    case_count: int,
    corpus: Sequence[CorpusDoc],
    harness_dir: str | None,
    skills: Sequence[str],
    lessons_file: str | None,
    lessons_count: int,
) -> dict[str, object]:
    """本轮跑分的配置指纹（#187 UoW-2）：随报告归档，是对照可比性的依据。

    关键项（model / casesFile / corpus）决定两份报告能否直接比分数，
    ``build_baseline_diff`` 对这三项不一致显式警告；harnessDir / skills /
    lessons 是对照实验本身要变的臂，只记录不警告。harnessDir 记实际生效
    值：未显式指定时即组装缺省目录（与 assemble_harness 同一解析）。
    cases_file / lessons_file 是展示路径：函数注入（测试/编程调用）时为
    None，表示「未标注」，不冒充某个文件。
    """
    return {
        "model": model_name,
        "passRule": PASS_RULE,
        "casesFile": {"path": cases_file, "count": case_count},
        "corpus": corpus_fingerprint(corpus),
        "harnessDir": harness_dir or default_harness_directory(),
        "skills": list(skills),
        "lessons": {"file": lessons_file, "count": lessons_count},
    }


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
        _validate_case_extras(case)


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
    skills_dir: str | None = None,
    skills_self_select_enabled: bool = False,
) -> dict[str, object]:
    # 与生产路径一致：知识检索由 agent 经 search_site_knowledge 工具执行，
    # 命中片段经 collector 回流生成 citations；评测量的就是这条链。
    # skills / harness_dir / experience_lessons 同样按生产口径透传 run_chat：
    # skill_resolution 由调用方经 agent.skills.resolve_skills 解析（与
    # api/internal.py 同一函数、同一输入），不在 runner 内另造旁路。
    # 多轮题的 history（#190）同理：题目字段即 ChatHistoryMessage 列表，
    # 原样作 run_chat 的 history 入参，不做改写。
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset(agent_id, service, collector=collector)
    toolsets: list[object] = [toolset]
    # 自选场景（Issue #195）：与生产 toolset_factory 同一工具、同一收集器，
    # 开关来自进程 Settings（main 透传）；关时不注册，run 与接通前一致。
    skill_collector: SkillLoadCollector | None = None
    if skills_self_select_enabled and skills_dir:
        skill_collector = SkillLoadCollector()
        toolsets.append(
            build_skill_toolset(
                skills_dir,
                collector=skill_collector,
                preselected_ids=(
                    list(skill_resolution.applied_ids) if skill_resolution else ()
                ),
                max_instruction_chars=skills_max_instruction_chars,
            )
        )
    outcome = await run_chat(
        model,
        agent_id=agent_id,
        message=case.question,
        history=list(case.history) if case.history else None,
        toolsets=toolsets,
        knowledge_collector=collector,
        memory_extraction_enabled=False,
        skill_instructions=skill_resolution.instructions if skill_resolution else None,
        skill_ids=list(skill_resolution.applied_ids) if skill_resolution else None,
        skills_missing=list(skill_resolution.missing_ids) if skill_resolution else None,
        skills_max_instruction_chars=skills_max_instruction_chars,
        harness_dir=harness_dir,
        experience_lessons=experience_lessons,
        skill_catalog=(
            list_skill_catalog(skills_dir)
            if skill_collector is not None and skills_dir
            else None
        ),
        skills_self_select_enabled=skill_collector is not None,
        skill_catalog_exclude_ids=(
            tuple(skill_resolution.applied_ids) if skill_resolution else ()
        ),
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
        behavior_checks=case.behavior_checks,
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
    # 多轮题把先前轮次原样附进报告行，供人工核对承接关系（#190 UoW-2）。
    if case.history:
        row["history"] = [
            {"role": item.role, "content": item.content} for item in case.history
        ]
    # 行为检查逐项结果只在题目声明了 behavior_checks 时出现（#190 UoW-1）；
    # 与 score_case 内部计入 passed 的是同一份计算，口径不会漂移。
    if case.behavior_checks:
        row["behaviorChecks"] = evaluate_behavior_checks(
            case.behavior_checks,
            answer=answer,
            expected_sources=case.expected_sources,
            citation_source_ids=citation_source_ids,
        )
    # 警告级（不进 pass）：引用精度过低时附在行上，汇总另行单列。
    precision_value = scores.get("citation_precision")
    warnings = case_warnings(
        citation_precision=precision_value
        if isinstance(precision_value, (int, float))
        and not isinstance(precision_value, bool)
        else None
    )
    if warnings:
        row["warnings"] = warnings
    # skill 题的触发/完成标注只在题面带 skill 字段时出现，黄金题行形状不变。
    # skillsApplied 即本轮解析的 applied 集，与生产响应 skillsApplied 同源；
    # 自选开启时再合并 load_skill 加载集（同一 merge 口径）。
    if case.skill:
        applied = merge_skills_applied(
            list(skill_resolution.applied_ids) if skill_resolution else [],
            skill_collector.loaded_ids if skill_collector is not None else None,
        )
        triggered = case.skill in applied
        row["skill"] = case.skill
        row["kind"] = case.kind
        row["skillsApplied"] = applied
        if skill_resolution and skill_resolution.missing_ids:
            row["skillsMissing"] = list(skill_resolution.missing_ids)
        row["triggered"] = triggered
        row["completed"] = bool(triggered and scores["passed"])
    return row


def _unscored_row(
    case: GoldenCase, exc: BaseException, *, timeout_seconds: float
) -> dict[str, object]:
    """单题运行失败时的报告行：记 unscored，不记 0 分（#187 UoW-2）。

    口径（借 Harness² judge 完整性）：异常/超时的题没有产生可判的回答，
    scores 四指标与 passed 全为 None——聚合时不进均值分母，也不会被
    diff 读成 pass→fail 翻转；reason 保留异常类型与信息供人工归因。
    行的其余字段与正常行同形状，渲染与下游消费不需要特判结构。
    """
    if isinstance(exc, TimeoutError):
        reason = f"timeout after {timeout_seconds:g} seconds (per-case limit)"
    else:
        detail = str(exc).strip()
        reason = f"{type(exc).__name__}: {detail}" if detail else type(exc).__name__
    row: dict[str, object] = {
        "id": case.id,
        "category": case.category,
        "question": case.question,
        "expectedSources": list(case.expected_sources),
        "reply": "",
        "blockedReason": None,
        "errorCode": None,
        "retrievalDegraded": False,
        "retrieved": [],
        "citations": None,
        "unscored": True,
        "unscoredReason": reason,
        "scores": {
            "retrieval_hit": None,
            "citation_precision": None,
            "keyword_coverage": None,
            "refusal_correct": None,
            "passed": None,
        },
    }
    if case.expected_behavior:
        row["expectedBehavior"] = case.expected_behavior
    if case.expected_answer:
        row["expectedAnswer"] = case.expected_answer
    if case.history:
        row["history"] = [
            {"role": item.role, "content": item.content} for item in case.history
        ]
    # skill 题保留题面标注，但 triggered/completed 描述的是运行结果，
    # 本题没有结果，不填（报告读作 n/a），更不能记成未触发。
    if case.skill:
        row["skill"] = case.skill
        row["kind"] = case.kind
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
    # 配置指纹（#187 UoW-2）：旧报告无此块时整段跳过，不影响其渲染。
    fingerprint = report.get("fingerprint")
    if isinstance(fingerprint, dict):
        cases_fp = fingerprint.get("casesFile")
        if isinstance(cases_fp, dict):
            cases_path = cases_fp.get("path")
            if isinstance(cases_path, str) and cases_path:
                lines.append(f"- Cases file: `{cases_path}`（{cases_fp.get('count')} 题）")
            else:
                lines.append(f"- Cases: {cases_fp.get('count')} 题（路径未标注）")
        corpus_fp = fingerprint.get("corpus")
        if isinstance(corpus_fp, dict):
            sha = str(corpus_fp.get("sha256") or "")
            lines.append(
                f"- Corpus: {corpus_fp.get('documents')} documents · sha256 `{sha[:12]}…`"
            )
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
    # unscored 单列（#187 UoW-2）：这些题没有分数，不混进上表均值。
    unscored_cases = summary.get("unscoredCases")
    if isinstance(unscored_cases, list) and unscored_cases:
        lines.extend(
            [
                "",
                "## Unscored",
                "",
                "下列题目运行异常或超时，未计分、不进均值（口径见 evals/README.md）：",
                "",
                "| id | category | reason |",
                "| --- | --- | --- |",
            ]
        )
        for entry in unscored_cases:
            if not isinstance(entry, dict):
                continue
            lines.append(
                "| "
                + " | ".join(
                    [
                        _cell(entry.get("id")),
                        _cell(entry.get("category")),
                        _cell(entry.get("reason")),
                    ]
                )
                + " |"
            )
    # 行为检查逐项结果（#190 UoW-1）：只列声明了 behavior_checks 的题。
    behavior_rows = [
        case for case in case_rows if isinstance(case.get("behaviorChecks"), dict)
    ]
    if behavior_rows:
        lines.extend(
            [
                "",
                "## Behavior checks",
                "",
                "声明了 behavior_checks 的题逐项结果（计入 pass；n/a 为判不出、不计）：",
                "",
                "| id | check | result |",
                "| --- | --- | --- |",
            ]
        )
        for case in behavior_rows:
            checks = case.get("behaviorChecks")
            if not isinstance(checks, dict):
                continue
            for name, value in checks.items():
                lines.append(
                    "| "
                    + " | ".join([_cell(case.get("id")), _cell(name), _cell(value)])
                    + " |"
                )
    # 警告级单列（#190 UoW-1）：不影响 pass，只提示引用面噪声。
    warning_cases = summary.get("warningCases")
    if isinstance(warning_cases, list) and warning_cases:
        lines.extend(
            [
                "",
                "## Warnings",
                "",
                "下列题目带警告（不影响 pass，口径见 evals/README.md）：",
                "",
                "| id | category | warnings |",
                "| --- | --- | --- |",
            ]
        )
        for entry in warning_cases:
            if not isinstance(entry, dict):
                continue
            warnings_value = entry.get("warnings")
            warnings_text = (
                ", ".join(str(item) for item in warnings_value)
                if isinstance(warnings_value, list)
                else ""
            )
            lines.append(
                "| "
                + " | ".join(
                    [
                        _cell(entry.get("id")),
                        _cell(entry.get("category")),
                        _cell(warnings_text),
                    ]
                )
                + " |"
            )
    baseline_diff = report.get("baselineDiff")
    if isinstance(baseline_diff, dict):
        lines.extend(_render_baseline_diff(baseline_diff))
    lines.append("")
    return "\n".join(lines)


def load_baseline_report(path: Path) -> dict[str, object]:
    """加载 --baseline 的基线报告 JSON；缺失/坏形状一律明确报错，不静默当无基线。"""
    try:
        raw_text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ValueError(f"--baseline cannot be read: {path} ({exc}).") from exc
    try:
        raw = json.loads(raw_text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"--baseline is not valid JSON: {path} ({exc}).") from exc
    if not isinstance(raw, dict):
        raise ValueError("--baseline must be a report JSON object.")
    if not isinstance(raw.get("cases"), list):
        raise ValueError("--baseline report has no cases list; not an eval report.")
    return raw


def _num(value: object) -> float | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return 1.0 if value else 0.0
    if isinstance(value, (int, float)):
        return float(value)
    return None


def _metric_delta(baseline_value: object, current_value: object) -> dict[str, object]:
    base_num = _num(baseline_value)
    current_num = _num(current_value)
    delta = (
        round(current_num - base_num, 6)
        if base_num is not None and current_num is not None
        else None
    )
    return {"baseline": baseline_value, "current": current_value, "delta": delta}


def _report_fingerprint(report: dict[str, object]) -> dict[str, object]:
    """取报告指纹；UoW-2 之前的旧报告没有 fingerprint 块，从遗留顶层键派生。

    派生时 casesFile / corpus 记 None（当时没记、无从核），diff 的警告逻辑
    会把「核不了」显式报出来，而不是假装同配置。
    """
    fingerprint = report.get("fingerprint")
    if isinstance(fingerprint, dict):
        return fingerprint
    skills_value = report.get("skills")
    lessons_count = report.get("lessonsCount")
    return {
        "model": report.get("model"),
        "passRule": report.get("passRule"),
        "casesFile": None,
        "corpus": None,
        "harnessDir": report.get("harnessDir"),
        "skills": list(skills_value) if isinstance(skills_value, list) else [],
        "lessons": {
            "file": None,
            "count": lessons_count if isinstance(lessons_count, int) else 0,
        },
    }


def _cases_file_identity(fingerprint: dict[str, object]) -> tuple[str, object] | None:
    """题集身份＝（路径 basename，题数）；路径未标注时只有题数可比。"""
    cases_fp = fingerprint.get("casesFile")
    if not isinstance(cases_fp, dict):
        return None
    path = cases_fp.get("path")
    basename = Path(path).name if isinstance(path, str) and path else None
    return (basename or "", cases_fp.get("count"))


def _corpus_sha(fingerprint: dict[str, object]) -> str | None:
    corpus_fp = fingerprint.get("corpus")
    if not isinstance(corpus_fp, dict):
        return None
    sha = corpus_fp.get("sha256")
    return sha if isinstance(sha, str) and sha else None


def _fingerprint_warnings(
    current_fp: dict[str, object],
    baseline_fp: dict[str, object],
    *,
    baseline_has_fingerprint: bool,
) -> list[str]:
    """关键项（model / casesFile / corpus）不一致或无从核对时的警告。

    harnessDir / skills / lessons 是对照实验要变的臂，不在此列。警告不
    阻断 diff 产出，但标注分数不可直接比——是否采信由人结合警告判断。
    """
    warnings: list[str] = []
    if not baseline_has_fingerprint:
        warnings.append(
            "baseline report has no fingerprint block (pre-UoW-2 report); "
            "cases/corpus identity cannot be verified."
        )
    current_model = current_fp.get("model")
    baseline_model = baseline_fp.get("model")
    if current_model != baseline_model:
        warnings.append(
            f"model differs (baseline={baseline_model!r}, current={current_model!r}); "
            "scores are not directly comparable."
        )
    current_cases = _cases_file_identity(current_fp)
    baseline_cases = _cases_file_identity(baseline_fp)
    if current_cases is None or baseline_cases is None:
        if baseline_has_fingerprint:
            warnings.append("cases file identity is missing on one side; cannot verify.")
    elif current_cases != baseline_cases:
        warnings.append(
            f"cases file differs (baseline={baseline_cases!r}, current={current_cases!r}); "
            "scores are not directly comparable."
        )
    current_sha = _corpus_sha(current_fp)
    baseline_sha = _corpus_sha(baseline_fp)
    if current_sha is None or baseline_sha is None:
        if baseline_has_fingerprint:
            warnings.append("corpus identity is missing on one side; cannot verify.")
    elif current_sha != baseline_sha:
        warnings.append(
            "corpus content differs (sha256 mismatch); scores are not directly comparable."
        )
    return warnings


def _rows_by_id(report: dict[str, object]) -> dict[str, dict[str, object]]:
    cases = report.get("cases")
    rows: dict[str, dict[str, object]] = {}
    if isinstance(cases, list):
        for row in cases:
            if isinstance(row, dict) and isinstance(row.get("id"), str):
                rows[str(row["id"])] = row
    return rows


def _row_passed(row: dict[str, object]) -> bool | None:
    scores = row.get("scores")
    if not isinstance(scores, dict):
        return None
    passed = scores.get("passed")
    return passed if isinstance(passed, bool) else None


def _row_score(row: dict[str, object], name: str) -> object:
    scores = row.get("scores")
    if not isinstance(scores, dict):
        return None
    return scores.get(name)


def _overall_value(report: dict[str, object], name: str) -> object:
    summary = report.get("summary")
    if not isinstance(summary, dict):
        return None
    overall = summary.get("overall")
    if not isinstance(overall, dict):
        return None
    return overall.get(name)


_SCORE_METRICS: tuple[str, ...] = tuple(name for name in METRIC_NAMES if name != "passed")


def build_baseline_diff(
    current: dict[str, object], baseline: dict[str, object]
) -> dict[str, object]:
    """逐题 diff 本轮报告与基线报告（#187 UoW-2）。

    翻转只认两边都有真实 pass 状态的题：本轮 unscored 的题单列（不是
    fail、不算翻转）；基线 unscored 的题同理单列。基线有而本轮缺的题、
    本轮新增题单列说明，不混入翻转判定。指纹关键项不一致时 warnings
    非空——diff 照常产出，但分数不可直接比。
    """
    current_rows = _rows_by_id(current)
    baseline_rows = _rows_by_id(baseline)
    current_fp = _report_fingerprint(current)
    baseline_fp = _report_fingerprint(baseline)
    warnings = _fingerprint_warnings(
        current_fp,
        baseline_fp,
        baseline_has_fingerprint=isinstance(baseline.get("fingerprint"), dict),
    )
    case_diffs: list[dict[str, object]] = []
    pass_to_fail: list[str] = []
    fail_to_pass: list[str] = []
    added: list[str] = []
    missing: list[str] = []
    unscored_now: list[str] = []
    unscored_base: list[str] = []
    ordered_ids = list(current_rows) + [
        case_id for case_id in baseline_rows if case_id not in current_rows
    ]
    for case_id in ordered_ids:
        cur = current_rows.get(case_id)
        base = baseline_rows.get(case_id)
        if cur is None:
            missing.append(case_id)
            case_diffs.append(
                {
                    "id": case_id,
                    "status": "missing",
                    "baselinePassed": _row_passed(base) if base else None,
                    "currentPassed": None,
                    "metricDeltas": {},
                }
            )
            continue
        if base is None:
            added.append(case_id)
            case_diffs.append(
                {
                    "id": case_id,
                    "status": "added",
                    "baselinePassed": None,
                    "currentPassed": _row_passed(cur),
                    "metricDeltas": {},
                }
            )
            continue
        base_passed = _row_passed(base)
        cur_passed = _row_passed(cur)
        if cur.get("unscored"):
            status = "unscored"
            unscored_now.append(case_id)
        elif base.get("unscored"):
            status = "baseline_unscored"
            unscored_base.append(case_id)
        elif base_passed is True and cur_passed is False:
            status = "pass_to_fail"
            pass_to_fail.append(case_id)
        elif base_passed is False and cur_passed is True:
            status = "fail_to_pass"
            fail_to_pass.append(case_id)
        else:
            status = "unchanged"
        case_diffs.append(
            {
                "id": case_id,
                "status": status,
                "baselinePassed": base_passed,
                "currentPassed": cur_passed,
                "metricDeltas": {
                    name: _metric_delta(_row_score(base, name), _row_score(cur, name))
                    for name in _SCORE_METRICS
                },
            }
        )
    return {
        "baselineModel": baseline.get("model"),
        "baselineGeneratedAt": baseline.get("generatedAt"),
        "fingerprint": {"current": current_fp, "baseline": baseline_fp},
        "fingerprintWarnings": warnings,
        "overall": {
            name: _metric_delta(_overall_value(baseline, name), _overall_value(current, name))
            for name in METRIC_NAMES
        },
        "flips": {"passToFail": pass_to_fail, "failToPass": fail_to_pass},
        "addedCases": added,
        "missingCases": missing,
        "unscoredCases": unscored_now,
        "baselineUnscoredCases": unscored_base,
        "cases": case_diffs,
    }


def _ids_cell(ids: object) -> str:
    if not isinstance(ids, list) or not ids:
        return "无"
    return "、".join(f"`{item}`" for item in ids)


def _render_baseline_diff(diff: dict[str, object]) -> list[str]:
    lines = [
        "",
        "## Baseline diff",
        "",
        f"- Baseline model: `{diff.get('baselineModel')}`",
        f"- Baseline generated: {diff.get('baselineGeneratedAt')}",
    ]
    warnings = diff.get("fingerprintWarnings")
    if isinstance(warnings, list):
        for warning in warnings:
            lines.append(f"- ⚠ 指纹警告：{warning}")
    overall = diff.get("overall")
    if isinstance(overall, dict):
        lines.extend(
            [
                "",
                "| metric | baseline | current | delta |",
                "| --- | --- | --- | --- |",
            ]
        )
        for name in METRIC_NAMES:
            entry = overall.get(name)
            if not isinstance(entry, dict):
                continue
            lines.append(
                "| "
                + " | ".join(
                    [
                        name,
                        _cell(entry.get("baseline")),
                        _cell(entry.get("current")),
                        _cell(entry.get("delta")),
                    ]
                )
                + " |"
            )
    flips = diff.get("flips")
    if isinstance(flips, dict):
        lines.extend(
            [
                "",
                "### Pass 翻转",
                "",
                f"- pass → fail：{_ids_cell(flips.get('passToFail'))}",
                f"- fail → pass：{_ids_cell(flips.get('failToPass'))}",
            ]
        )
    singles: list[tuple[str, str]] = [
        ("本轮新增（基线无此题）", "addedCases"),
        ("本轮缺失（基线有此题）", "missingCases"),
        ("本轮 unscored（不算翻转）", "unscoredCases"),
        ("基线 unscored（不算翻转）", "baselineUnscoredCases"),
    ]
    single_lines = [
        f"- {label}：{_ids_cell(diff.get(key))}"
        for label, key in singles
        if isinstance(diff.get(key), list) and diff.get(key)
    ]
    if single_lines:
        lines.extend(["", "### 单边与未计分", "", *single_lines])
    case_diffs = diff.get("cases")
    if isinstance(case_diffs, list):
        changed = []
        for entry in case_diffs:
            if not isinstance(entry, dict):
                continue
            deltas = entry.get("metricDeltas")
            has_delta = isinstance(deltas, dict) and any(
                isinstance(item, dict) and item.get("delta") not in (None, 0, 0.0)
                for item in deltas.values()
            )
            if entry.get("status") not in ("unchanged",) or has_delta:
                changed.append(entry)
        if changed:
            lines.extend(
                [
                    "",
                    "### 逐题变化（仅翻转、单边或指标有变）",
                    "",
                    "| id | status | passed（基线 → 本轮） | Δretrieval_hit | Δcitation_precision | Δkeyword_coverage | Δrefusal_correct |",
                    "| --- | --- | --- | --- | --- | --- | --- |",
                ]
            )
            for entry in changed:
                deltas = entry.get("metricDeltas")
                if not isinstance(deltas, dict):
                    deltas = {}
                cells = [
                    _cell(entry.get("id")),
                    _cell(entry.get("status")),
                    f"{_cell(entry.get('baselinePassed'))} → {_cell(entry.get('currentPassed'))}",
                ]
                for name in _SCORE_METRICS:
                    item = deltas.get(name)
                    cells.append(
                        _cell(item.get("delta")) if isinstance(item, dict) else "n/a"
                    )
                lines.append("| " + " | ".join(cells) + " |")
    return lines


def _run_exit_code(report: dict[str, object]) -> int:
    """整轮退出码（#187 UoW-2）：全 unscored 视同 BLOCKED(2)；相对基线
    出现 pass→fail 翻转记 3；其余 0。unscored 不算 fail 翻转。"""
    cases = report.get("cases")
    rows = (
        [row for row in cases if isinstance(row, dict)] if isinstance(cases, list) else []
    )
    if rows and all(row.get("unscored") for row in rows):
        return BLOCKED_EXIT
    diff = report.get("baselineDiff")
    if isinstance(diff, dict):
        flips = diff.get("flips")
        if isinstance(flips, dict):
            pass_to_fail = flips.get("passToFail")
            if isinstance(pass_to_fail, list) and pass_to_fail:
                return REGRESSION_EXIT
    return 0


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
    skills_self_select_enabled: bool = False,
    cases_file: str | None = None,
    lessons_file: str | None = None,
    baseline_report: dict[str, object] | None = None,
    case_timeout_seconds: float = CASE_TIMEOUT_SECONDS,
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
    - ``skills_self_select_enabled``：CLI 层从进程 Settings 的
      ``AGENT_SKILLS_SELF_SELECT_ENABLED`` 传入（#195）；开时每题 run 注册
      与生产同一组 skill 工具并注入 catalog，``mode: "self-select"`` 的题
      不预选追加题面 skill，triggered 读合并后的 skillsApplied。

    对照参数（#187 UoW-2）：

    - ``cases_file`` / ``lessons_file``：仅作指纹里的展示路径（函数注入
      时为 None＝未标注），不影响跑分本身。
    - ``baseline_report``：基线报告 dict（CLI ``--baseline`` 已加载）；
      给出时报告带 ``baselineDiff`` 逐题对照（见 build_baseline_diff）。
    - ``case_timeout_seconds``：单题墙钟上限，超限记 unscored。
      每题独立隔离：单题异常/超时只记该题 unscored，整轮继续。
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
        # 自选题（#195）不追加题面 skill：预选了就没有「自选」可测。
        if case.skill and case.mode != "self-select" and case.skill not in requested:
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
            # 逐题隔离（#187 UoW-2）：单题异常/超时只记该题 unscored，
            # 不中断整轮、不记 0 分（见 _unscored_row 的口径）。
            try:
                resolution = resolution_for(case)
                row = await asyncio.wait_for(
                    _run_case(
                        service,
                        model,
                        case,
                        agent_id=agent_id,
                        skill_resolution=resolution,
                        skills_max_instruction_chars=skills_max_instruction_chars,
                        harness_dir=harness_dir,
                        experience_lessons=lessons,
                        skills_dir=resolved_skills_dir,
                        skills_self_select_enabled=skills_self_select_enabled,
                    ),
                    timeout=case_timeout_seconds,
                )
            except Exception as exc:  # noqa: BLE001 — 单题失败隔离，整轮继续
                logger.warning("eval case %s unscored: %s", case.id, exc)
                row = _unscored_row(case, exc, timeout_seconds=case_timeout_seconds)
            rows.append(row)
    finally:
        await service.close()
    scored_rows = [row for row in rows if not row.get("unscored")]
    summary = aggregate(
        [{"category": row["category"], **row["scores"]} for row in scored_rows if isinstance(row["scores"], dict)]
    )
    unscored_entries = [
        {"id": row["id"], "category": row["category"], "reason": row["unscoredReason"]}
        for row in rows
        if row.get("unscored")
    ]
    if unscored_entries:
        summary["unscoredCount"] = len(unscored_entries)
        summary["unscoredCases"] = unscored_entries
    warning_entries = [
        {"id": row["id"], "category": row["category"], "warnings": row["warnings"]}
        for row in rows
        if isinstance(row.get("warnings"), list) and row["warnings"]
    ]
    if warning_entries:
        summary["warningCount"] = len(warning_entries)
        summary["warningCases"] = warning_entries
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
    if skills_self_select_enabled:
        report["skillsSelfSelect"] = True
    if harness_dir:
        report["harnessDir"] = harness_dir
    if lessons:
        report["lessonsCount"] = len(lessons)
    # 配置指纹（#187 UoW-2）：每份报告都带，随报告归档。
    report["fingerprint"] = build_fingerprint(
        model_name=model_name,
        cases_file=cases_file,
        case_count=len(loaded_cases),
        corpus=loaded_corpus,
        harness_dir=harness_dir,
        skills=base_skills,
        lessons_file=lessons_file,
        lessons_count=len(lessons) if lessons else 0,
    )
    if baseline_report is not None:
        report["baselineDiff"] = build_baseline_diff(report, baseline_report)
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
    parser.add_argument(
        "--baseline",
        type=Path,
        default=None,
        help="Baseline report JSON (a previous run's answer-eval-*.json) to diff "
        "against: per-case metric deltas and pass flips are attached to the new "
        "report. Exit code 3 when any case flips pass→fail vs the baseline.",
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
    baseline_report: dict[str, object] | None = None
    if args.baseline is not None:
        try:
            baseline_report = load_baseline_report(args.baseline)
        except ValueError as exc:
            print(f"ERROR: {exc}", file=sys.stderr, flush=True)
            return BLOCKED_EXIT
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
            skills_self_select_enabled=settings.agent_skills_self_select_enabled,
            cases_file=str(args.cases)
            if args.cases is not None
            else str(evals_root() / "golden_qa.jsonl"),
            lessons_file=str(args.lessons_file) if args.lessons_file is not None else None,
            baseline_report=baseline_report,
        )
    )
    if result.json_path is not None:
        print(result.json_path, flush=True)
    if result.markdown_path is not None:
        print(result.markdown_path, flush=True)
    diff = result.report.get("baselineDiff")
    if isinstance(diff, dict):
        warnings = diff.get("fingerprintWarnings")
        if isinstance(warnings, list):
            for warning in warnings:
                print(f"WARNING: {warning}", file=sys.stderr, flush=True)
    code = _run_exit_code(result.report)
    if code == BLOCKED_EXIT:
        report_cases = result.report.get("cases")
        total = len(report_cases) if isinstance(report_cases, list) else 0
        print(
            blocked_text(
                f"all {total} cases were unscored (per-case errors or timeouts); "
                "see the report's Unscored section for reasons."
            ),
            flush=True,
        )
    elif code == REGRESSION_EXIT:
        flips = diff.get("flips") if isinstance(diff, dict) else None
        flipped = flips.get("passToFail") if isinstance(flips, dict) else None
        print(
            "REGRESSION: cases flipped pass→fail vs baseline: "
            f"{', '.join(str(item) for item in flipped) if isinstance(flipped, list) else '?'}",
            file=sys.stderr,
            flush=True,
        )
    return code


if __name__ == "__main__":
    sys.exit(main())
