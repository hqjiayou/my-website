"""离线答案质量评估。

用法（在 agent-services 目录）::

    uv run python -m agent_services.evals.run_answer_eval
    uv run python -m agent_services.evals.run_answer_eval --out evals/reports

读取进程 Settings 的 AI_MODEL 与凭证。未配置模型时打印 BLOCKED 并以状态码 2 退出，
不用假模型冒充跑分。核心函数 ``run_answer_eval`` 接受注入的模型，供 CI smoke 使用。
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

from pydantic_ai.models import Model

from agent_services.agent.model_factory import create_model
from agent_services.agent.runtime import ChatBlocked, ChatReply, run_chat
from agent_services.config import RETRIEVAL_MIN_SCORE_DEFAULT, Settings
from agent_services.evals.scoring import METRIC_NAMES, PASS_RULE, aggregate, score_case
from agent_services.retrieval.service import RetrievalService, query_terms
from agent_services.retrieval.store import RetrievedChunk

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
            )
        )
    return cases


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
    site_id: str
    source_id: str
    chunk_index: int
    content: str
    embedding: list[float]


class MemoryRetrievalStore:
    """进程内索引。向量按余弦排序，词法按字面量子串命中数排序，都强制 site_id。"""

    def __init__(self) -> None:
        self._rows: list[_Row] = []
        self._titles: dict[tuple[str, str], str] = {}
        self._dimensions: int | None = None

    async def upsert_document(
        self,
        *,
        site_id: str,
        source_id: str,
        title: str,
        chunks: list[str],
        embeddings: list[list[float]],
    ) -> int:
        self._rows = [
            row for row in self._rows if not (row.site_id == site_id and row.source_id == source_id)
        ]
        self._titles[(site_id, source_id)] = title
        for index, (content, embedding) in enumerate(zip(chunks, embeddings, strict=True)):
            self._rows.append(
                _Row(
                    site_id=site_id,
                    source_id=source_id,
                    chunk_index=index,
                    content=content,
                    embedding=list(embedding),
                )
            )
            self._dimensions = len(embedding)
        return len(chunks)

    async def delete_document(self, *, site_id: str, source_id: str) -> None:
        self._rows = [
            row for row in self._rows if not (row.site_id == site_id and row.source_id == source_id)
        ]
        self._titles.pop((site_id, source_id), None)

    async def search(self, *, site_id: str, embedding: list[float], top_k: int) -> list[RetrievedChunk]:
        if top_k < 1:
            return []
        scored = [
            (_cosine(row.embedding, embedding), row) for row in self._rows if row.site_id == site_id
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
        self, *, site_id: str, tokens: list[str], limit: int
    ) -> list[RetrievedChunk]:
        if not tokens or limit < 1:
            return []
        ranked: list[tuple[int, _Row]] = []
        for row in self._rows:
            if row.site_id != site_id:
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
    corpus: Sequence[CorpusDoc], *, site_id: str = EVAL_SITE_ID
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
            site_id=site_id,
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
    service: RetrievalService, model: Model, case: GoldenCase, *, site_id: str
) -> dict[str, object]:
    # 与 internal chat 的向量 grounding 相同：降级或没有命中时不把 chunks 交给 runtime。
    chunks = await service.retrieve_chunks(site_id=site_id, query=case.question)
    degraded = service.degraded
    if degraded or not chunks:
        context: list[str] | None = None
        used: list[RetrievedChunk] | None = None
    else:
        context = [chunk.content for chunk in chunks]
        used = list(chunks)
    outcome = await run_chat(
        model,
        site_id=site_id,
        message=case.question,
        retrieval_context=context,
        retrieval_chunks=used,
        memory_extraction_enabled=False,
    )
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
    return {
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
        "",
        "没有 citations 的 citation_precision、没有关键词的 keyword_coverage、",
        "非不可答题的 refusal_correct、没有期望来源的 retrieval_hit 记 n/a，不进均值。",
        "",
        "## Overall",
        "",
        "| metric | mean |",
        "| --- | --- |",
    ]
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
    lines.extend(
        [
            "",
            "## Cases",
            "",
            "| id | category | passed | retrieval_hit | citation_precision | keyword_coverage | refusal_correct |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
    )
    cases = report["cases"]
    if isinstance(cases, list):
        for case in cases:
            if not isinstance(case, dict):
                continue
            scores = case.get("scores")
            if not isinstance(scores, dict):
                scores = {}
            lines.append(
                "| "
                + " | ".join(
                    [
                        _cell(case.get("id")),
                        _cell(case.get("category")),
                        _cell(scores.get("passed")),
                        _cell(scores.get("retrieval_hit")),
                        _cell(scores.get("citation_precision")),
                        _cell(scores.get("keyword_coverage")),
                        _cell(scores.get("refusal_correct")),
                    ]
                )
                + " |"
            )
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
    site_id: str = EVAL_SITE_ID,
) -> AnswerEvalRun:
    """对黄金集跑检索 + run_chat，评分，并在给出 out 时写下 JSON 与 Markdown。"""
    loaded_corpus = list(corpus) if corpus is not None else load_corpus()
    loaded_cases = list(cases) if cases is not None else load_cases()
    validate_golden(loaded_corpus, loaded_cases)
    when = _stamp(generated_at or datetime.now(UTC))
    service = await build_eval_index(loaded_corpus, site_id=site_id)
    rows: list[dict[str, object]] = []
    try:
        for case in loaded_cases:
            rows.append(await _run_case(service, model, case, site_id=site_id))
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
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    model, spec, blocked = configured_model_block(Settings())
    if blocked or model is None:
        print(blocked or blocked_text("AI model is not configured (missing AI_MODEL)."), flush=True)
        return BLOCKED_EXIT
    destination = args.out if args.out is not None else evals_root() / "reports"
    result = asyncio.run(
        run_answer_eval(model, model_name=spec, out=destination)
    )
    if result.json_path is not None:
        print(result.json_path, flush=True)
    if result.markdown_path is not None:
        print(result.markdown_path, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
