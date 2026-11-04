"""#187 UoW-2：对照报告与评分完整性的确定性验收（假模型层）。

覆盖：报告指纹（字段/corpus 哈希稳定性）、--baseline 逐题 diff（翻转/
单边/unscored 不算翻转/指标 delta）、指纹不一致警告、门槛退出码
（0/2/3）、unscored 隔离（异常与超时、均值分母、全 unscored 按 BLOCKED）。
真模型对照读数不在 CI 内跑，留 UoW-3 本机执行。
"""

from __future__ import annotations

import asyncio
import json
from pathlib import Path

import pytest
from pydantic_ai.messages import (
    ModelMessage,
    ModelResponse,
    TextPart,
    ToolCallPart,
    ToolReturnPart,
    UserPromptPart,
)
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.evals import run_answer_eval as runner
from agent_services.evals.run_answer_eval import (
    build_baseline_diff,
    build_fingerprint,
    corpus_fingerprint,
    load_cases,
    render_markdown,
    run_answer_eval,
)
from agent_services.evals.scoring import PASS_RULE

# ---------------------------------------------------------------------------
# 指纹
# ---------------------------------------------------------------------------


def test_corpus_fingerprint_is_stable_and_content_sensitive() -> None:
    corpus = runner.load_corpus()
    first = corpus_fingerprint(corpus)
    assert first == corpus_fingerprint(list(reversed(corpus)))  # 顺序不敏感
    assert first["documents"] == len(corpus)
    assert isinstance(first["sha256"], str) and len(first["sha256"]) == 64

    changed = [corpus[0].__class__(corpus[0].source_id, corpus[0].title, corpus[0].content + "x")]
    changed.extend(corpus[1:])
    assert corpus_fingerprint(changed)["sha256"] != first["sha256"]


async def test_report_carries_full_fingerprint() -> None:
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(_Recorder().respond),
        model_name="function:uow2",
        cases=[case],
        cases_file="evals/golden_qa.jsonl",
    )
    fingerprint = result.report["fingerprint"]
    assert isinstance(fingerprint, dict)
    assert fingerprint["model"] == "function:uow2"
    assert fingerprint["passRule"] == PASS_RULE
    assert fingerprint["casesFile"] == {"path": "evals/golden_qa.jsonl", "count": 1}
    corpus = fingerprint["corpus"]
    assert isinstance(corpus, dict)
    assert corpus["documents"] == len(runner.load_corpus())
    assert corpus["sha256"] == corpus_fingerprint(runner.load_corpus())["sha256"]
    assert fingerprint["harnessDir"]  # 缺省记实际生效的默认目录
    assert fingerprint["skills"] == []
    assert fingerprint["lessons"] == {"file": None, "count": 0}

    markdown = render_markdown(result.report)
    assert "Cases file: `evals/golden_qa.jsonl`（1 题）" in markdown
    assert "Corpus:" in markdown and "sha256" in markdown


async def test_fingerprint_records_injection_arms() -> None:
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(_Recorder().respond),
        model_name="function:uow2",
        cases=[case],
        skills=["orchestration"],
        harness_dir="/tmp/harness-variant",
        cases_file=None,
    )
    fingerprint = result.report["fingerprint"]
    assert isinstance(fingerprint, dict)
    assert fingerprint["skills"] == ["orchestration"]
    assert fingerprint["harnessDir"] == "/tmp/harness-variant"
    cases_fp = fingerprint["casesFile"]
    assert isinstance(cases_fp, dict) and cases_fp["path"] is None
    assert "路径未标注" in render_markdown(result.report)


def test_build_fingerprint_matches_report_shape() -> None:
    corpus = runner.load_corpus()
    fingerprint = build_fingerprint(
        model_name="m",
        cases_file=None,
        case_count=3,
        corpus=corpus,
        harness_dir=None,
        skills=[],
        lessons_file="lessons.json",
        lessons_count=2,
    )
    assert fingerprint["lessons"] == {"file": "lessons.json", "count": 2}
    assert fingerprint["casesFile"] == {"path": None, "count": 3}


# ---------------------------------------------------------------------------
# diff（纯函数层，手工构造报告）
# ---------------------------------------------------------------------------


def _report(
    rows: list[dict[str, object]],
    *,
    model: str = "function:uow2",
    fingerprint: dict[str, object] | None = None,
) -> dict[str, object]:
    scored = [row for row in rows if not row.get("unscored")]
    report: dict[str, object] = {
        "model": model,
        "generatedAt": "2026-11-04T00:00:00+00:00",
        "passRule": PASS_RULE,
        "summary": runner.aggregate(
            [
                {"category": row["category"], **row["scores"]}  # type: ignore[dict-item]
                for row in scored
            ]
        ),
        "cases": rows,
    }
    if fingerprint is not None:
        report["fingerprint"] = fingerprint
    return report


def _scored_row(
    case_id: str,
    *,
    passed: bool,
    coverage: float | None = 1.0,
    category: str = "hours",
) -> dict[str, object]:
    return {
        "id": case_id,
        "category": category,
        "scores": {
            "retrieval_hit": passed,
            "citation_precision": None,
            "keyword_coverage": coverage,
            "refusal_correct": None,
            "passed": passed,
        },
    }


def _unscored_row_dict(case_id: str) -> dict[str, object]:
    return {
        "id": case_id,
        "category": "hours",
        "unscored": True,
        "unscoredReason": "RuntimeError: boom",
        "scores": {
            "retrieval_hit": None,
            "citation_precision": None,
            "keyword_coverage": None,
            "refusal_correct": None,
            "passed": None,
        },
    }


def _fingerprint(*, model: str = "function:uow2", cases_count: int = 4, sha: str = "a" * 64) -> dict[str, object]:
    return {
        "model": model,
        "passRule": PASS_RULE,
        "casesFile": {"path": "evals/golden_qa.jsonl", "count": cases_count},
        "corpus": {"documents": 13, "sha256": sha},
        "harnessDir": "/harness",
        "skills": [],
        "lessons": {"file": None, "count": 0},
    }


def test_diff_detects_flips_added_missing_and_deltas() -> None:
    baseline = _report(
        [
            _scored_row("flip-down", passed=True),
            _scored_row("flip-up", passed=False, coverage=0.25),
            _scored_row("same", passed=True),
            _scored_row("gone", passed=True),
        ],
        fingerprint=_fingerprint(),
    )
    current = _report(
        [
            _scored_row("flip-down", passed=False, coverage=0.0),
            _scored_row("flip-up", passed=True, coverage=0.75),
            _scored_row("same", passed=True),
            _scored_row("new", passed=False, coverage=0.0),
        ],
        fingerprint=_fingerprint(),
    )
    diff = build_baseline_diff(current, baseline)
    assert diff["flips"] == {"passToFail": ["flip-down"], "failToPass": ["flip-up"]}
    assert diff["addedCases"] == ["new"]
    assert diff["missingCases"] == ["gone"]
    assert diff["fingerprintWarnings"] == []

    by_id = {entry["id"]: entry for entry in diff["cases"]}  # type: ignore[union-attr]
    assert by_id["flip-down"]["status"] == "pass_to_fail"
    assert by_id["same"]["status"] == "unchanged"
    assert by_id["new"]["status"] == "added"
    assert by_id["gone"]["status"] == "missing"
    delta = by_id["flip-up"]["metricDeltas"]["keyword_coverage"]
    assert delta == {"baseline": 0.25, "current": 0.75, "delta": 0.5}
    overall = diff["overall"]  # type: ignore[index]
    assert overall["passed"]["baseline"] == 0.75
    assert overall["passed"]["current"] == 0.5
    assert overall["passed"]["delta"] == -0.25


def test_diff_unscored_is_never_a_flip() -> None:
    baseline = _report(
        [_scored_row("was-pass", passed=True), _unscored_row_dict("was-unscored")],
        fingerprint=_fingerprint(cases_count=2),
    )
    current = _report(
        [_unscored_row_dict("was-pass"), _scored_row("was-unscored", passed=True)],
        fingerprint=_fingerprint(cases_count=2),
    )
    diff = build_baseline_diff(current, baseline)
    assert diff["flips"] == {"passToFail": [], "failToPass": []}
    assert diff["unscoredCases"] == ["was-pass"]
    assert diff["baselineUnscoredCases"] == ["was-unscored"]
    by_id = {entry["id"]: entry for entry in diff["cases"]}  # type: ignore[union-attr]
    assert by_id["was-pass"]["status"] == "unscored"
    assert by_id["was-unscored"]["status"] == "baseline_unscored"


def test_diff_fingerprint_warnings() -> None:
    baseline = _report([_scored_row("a", passed=True)], fingerprint=_fingerprint())
    # 模型不同＋语料不同＋题数不同：三条警告，diff 仍产出。
    current = _report(
        [_scored_row("a", passed=False)],
        fingerprint=_fingerprint(model="other:model", cases_count=9, sha="b" * 64),
    )
    diff = build_baseline_diff(current, baseline)
    warnings = diff["fingerprintWarnings"]
    assert isinstance(warnings, list) and len(warnings) == 3
    joined = " ".join(str(item) for item in warnings)
    assert "model differs" in joined
    assert "cases file differs" in joined
    assert "corpus content differs" in joined
    assert diff["flips"] == {"passToFail": ["a"], "failToPass": []}

    # 基线是指纹之前的旧报告：显式警告无从核对，顶层 model 仍参与比对。
    legacy = _report([_scored_row("a", passed=True)], model="legacy:model")
    diff = build_baseline_diff(_report([_scored_row("a", passed=True)], fingerprint=_fingerprint()), legacy)
    warnings = diff["fingerprintWarnings"]
    assert isinstance(warnings, list) and len(warnings) == 2
    joined = " ".join(str(item) for item in warnings)
    assert "no fingerprint block" in joined
    assert "model differs" in joined


def test_diff_renders_into_markdown() -> None:
    baseline = _report([_scored_row("a", passed=True)], fingerprint=_fingerprint(cases_count=1))
    current = _report([_scored_row("a", passed=False)], fingerprint=_fingerprint(cases_count=1))
    current["baselineDiff"] = build_baseline_diff(current, baseline)
    markdown = render_markdown(current)
    assert "## Baseline diff" in markdown
    assert "pass → fail：`a`" in markdown


# ---------------------------------------------------------------------------
# 管线层：unscored 隔离
# ---------------------------------------------------------------------------


class _Recorder:
    """先检索、再据工具返回作答（与 wiring 测试同路径的假模型）。"""

    def respond(self, messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        question = ""
        for message in messages:
            for part in getattr(message, "parts", []):
                if (
                    isinstance(part, ToolReturnPart)
                    and part.tool_name == "search_site_knowledge"
                ):
                    content = part.content if isinstance(part.content, str) else ""
                    if not content or content.startswith("No matching"):
                        return ModelResponse(parts=[TextPart("无法确认。没有相关信息。")])
                    return ModelResponse(parts=[TextPart(content)])
                elif isinstance(part, UserPromptPart) and isinstance(part.content, str):
                    question = part.content
        return ModelResponse(parts=[ToolCallPart("search_site_knowledge", {"query": question})])


def _row(report: dict[str, object], case_id: str) -> dict[str, object]:
    rows = report["cases"]
    assert isinstance(rows, list)
    for row in rows:
        if isinstance(row, dict) and row.get("id") == case_id:
            return row
    raise AssertionError(f"row {case_id} missing")


async def test_single_case_exception_is_isolated_as_unscored(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    by_id = {case.id: case for case in load_cases()}
    wanted = [by_id["zh-proper-hours"], by_id["model-xj900"], by_id["unans-wifi"]]
    original = runner._run_case

    async def flaky(service, model, case, **kwargs):  # type: ignore[no-untyped-def]
        if case.id == "model-xj900":
            raise RuntimeError("simulated transport failure")
        return await original(service, model, case, **kwargs)

    monkeypatch.setattr(runner, "_run_case", flaky)
    result = await run_answer_eval(
        FunctionModel(_Recorder().respond), model_name="function:uow2", cases=wanted
    )
    report = result.report

    bad = _row(report, "model-xj900")
    assert bad["unscored"] is True
    assert "RuntimeError" in str(bad["unscoredReason"])
    assert "simulated transport failure" in str(bad["unscoredReason"])
    scores = bad["scores"]
    assert isinstance(scores, dict)
    assert all(scores[name] is None for name in scores)

    # 其余题正常出分：可答 pass、不可答拒答 pass。
    assert _row(report, "zh-proper-hours")["scores"]["passed"] is True  # type: ignore[index]
    assert _row(report, "unans-wifi")["scores"]["passed"] is True  # type: ignore[index]

    # unscored 不进均值分母：overall count 只数已计分题，passed 均值 1.0。
    summary = report["summary"]
    assert isinstance(summary, dict)
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 2
    assert overall["passed"] == 1.0
    assert summary["unscoredCount"] == 1
    unscored_cases = summary["unscoredCases"]
    assert isinstance(unscored_cases, list) and len(unscored_cases) == 1
    assert unscored_cases[0]["id"] == "model-xj900"

    markdown = render_markdown(report)
    assert "## Unscored" in markdown
    assert "model-xj900" in markdown


async def test_case_timeout_is_unscored() -> None:
    async def sleepy(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        await asyncio.sleep(30)
        return ModelResponse(parts=[TextPart("too late")])

    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(sleepy),
        model_name="function:uow2",
        cases=[case],
        case_timeout_seconds=0.5,
    )
    row = _row(result.report, "zh-proper-hours")
    assert row["unscored"] is True
    assert "timeout" in str(row["unscoredReason"])


async def test_all_unscored_run_has_empty_means_and_blocked_exit_code(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def always_fail(service, model, case, **kwargs):  # type: ignore[no-untyped-def]
        raise RuntimeError("everything is down")

    monkeypatch.setattr(runner, "_run_case", always_fail)
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(_Recorder().respond), model_name="function:uow2", cases=[case]
    )
    summary = result.report["summary"]
    assert isinstance(summary, dict)
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 0
    assert overall["passed"] is None
    assert summary["unscoredCount"] == 1
    assert runner._run_exit_code(result.report) == 2


def test_run_exit_code_matrix() -> None:
    plain = _report([_scored_row("a", passed=True)], fingerprint=_fingerprint(cases_count=1))
    assert runner._run_exit_code(plain) == 0

    flipped = _report([_scored_row("a", passed=False)], fingerprint=_fingerprint(cases_count=1))
    baseline = _report([_scored_row("a", passed=True)], fingerprint=_fingerprint(cases_count=1))
    flipped["baselineDiff"] = build_baseline_diff(flipped, baseline)
    assert runner._run_exit_code(flipped) == 3

    # 只有 fail→pass 时不触发门槛。
    improved = _report([_scored_row("a", passed=True)], fingerprint=_fingerprint(cases_count=1))
    worse = _report([_scored_row("a", passed=False)], fingerprint=_fingerprint(cases_count=1))
    improved["baselineDiff"] = build_baseline_diff(improved, worse)
    assert runner._run_exit_code(improved) == 0

    # 全 unscored 优先于翻转判定（无分数可言，先按 BLOCKED）。
    all_bad = _report([_unscored_row_dict("a")], fingerprint=_fingerprint(cases_count=1))
    all_bad["baselineDiff"] = build_baseline_diff(all_bad, baseline)
    assert runner._run_exit_code(all_bad) == 2


async def test_baseline_param_attaches_diff_without_flips() -> None:
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    first = await run_answer_eval(
        FunctionModel(_Recorder().respond), model_name="function:uow2", cases=[case]
    )
    second = await run_answer_eval(
        FunctionModel(_Recorder().respond),
        model_name="function:uow2",
        cases=[case],
        baseline_report=first.report,
    )
    diff = second.report["baselineDiff"]
    assert isinstance(diff, dict)
    assert diff["flips"] == {"passToFail": [], "failToPass": []}
    assert diff["fingerprintWarnings"] == []
    assert runner._run_exit_code(second.report) == 0
    assert "## Baseline diff" in render_markdown(second.report)


# ---------------------------------------------------------------------------
# CLI：退出码端到端（模型经 configured_model_block 注入假模型）
# ---------------------------------------------------------------------------


def _mini_cases_file(tmp_path: Path) -> Path:
    wanted = {"zh-proper-hours", "unans-wifi"}
    source = runner.evals_root() / "golden_qa.jsonl"
    lines = [
        line
        for line in source.read_text(encoding="utf-8").splitlines()
        if line.strip() and json.loads(line)["id"] in wanted
    ]
    assert len(lines) == 2
    path = tmp_path / "mini.jsonl"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def _install_model(monkeypatch: pytest.MonkeyPatch, model: FunctionModel, spec: str) -> None:
    monkeypatch.setattr(
        runner,
        "configured_model_block",
        lambda settings: (model, spec, ""),
    )


def _refusal(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
    return ModelResponse(parts=[TextPart("无法确认。没有相关信息。")])


def test_cli_exit_codes_with_baseline(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
    tmp_path: Path,
) -> None:
    cases_path = _mini_cases_file(tmp_path)
    _install_model(monkeypatch, FunctionModel(_Recorder().respond), "function:base")

    out1 = tmp_path / "run1"
    assert runner.main(["--out", str(out1), "--cases", str(cases_path)]) == 0
    baseline_path = next(out1.glob("*.json"))
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    assert _row(baseline, "zh-proper-hours")["scores"]["passed"] is True  # type: ignore[index]

    # 同行为再跑：无翻转 → 0。
    out2 = tmp_path / "run2"
    assert (
        runner.main(
            ["--out", str(out2), "--cases", str(cases_path), "--baseline", str(baseline_path)]
        )
        == 0
    )
    capsys.readouterr()

    # 换拒答模型：可答题 pass→fail → 3，stderr 明示 REGRESSION。
    _install_model(monkeypatch, FunctionModel(_refusal), "function:refusal")
    out3 = tmp_path / "run3"
    assert (
        runner.main(
            ["--out", str(out3), "--cases", str(cases_path), "--baseline", str(baseline_path)]
        )
        == 3
    )
    captured = capsys.readouterr()
    assert "REGRESSION" in captured.err
    assert "zh-proper-hours" in captured.err
    # 模型与基线不同，指纹警告同时出现在 stderr（不阻断判定）。
    assert "WARNING" in captured.err and "model differs" in captured.err


def test_cli_all_unscored_is_blocked_exit_2(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
    tmp_path: Path,
) -> None:
    async def always_fail(service, model, case, **kwargs):  # type: ignore[no-untyped-def]
        raise RuntimeError("everything is down")

    monkeypatch.setattr(runner, "_run_case", always_fail)
    _install_model(monkeypatch, FunctionModel(_Recorder().respond), "function:base")
    cases_path = _mini_cases_file(tmp_path)
    assert runner.main(["--out", str(tmp_path / "out"), "--cases", str(cases_path)]) == 2
    assert "BLOCKED" in capsys.readouterr().out


def test_cli_rejects_bad_baseline_before_running(
    capsys: pytest.CaptureFixture[str], tmp_path: Path
) -> None:
    assert runner.main(["--baseline", str(tmp_path / "missing.json")]) == 2
    assert "ERROR" in capsys.readouterr().err

    broken = tmp_path / "broken.json"
    broken.write_text("{not json", encoding="utf-8")
    assert runner.main(["--baseline", str(broken)]) == 2
    assert "ERROR" in capsys.readouterr().err

    not_report = tmp_path / "not-report.json"
    not_report.write_text('{"hello": 1}', encoding="utf-8")
    assert runner.main(["--baseline", str(not_report)]) == 2
    assert "ERROR" in capsys.readouterr().err
