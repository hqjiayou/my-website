"""#206：eval 第二批的确定性验收（假模型层）。

覆盖四处：UoW-1 断点续跑 --resume-from（已计分行逐字复用、未完成题
重跑、指纹闸逐项全等、pace 只作用于实际执行的题、CLI 文件错误与
指纹不一致退出 2）；UoW-2 真冲突文档对与 conf 正例锚点；
UoW-3 长文档形状（多段落、21 只在末段）与长文题锚点；
UoW-4 manualOnly 单列（进单列但仍留在 aggregate 分母、blocked 不
重复收录）。真模型读数不在 CI 内跑，仍属 #187 UoW-3 本机执行。
"""

from __future__ import annotations

import asyncio
import copy
import json
from pathlib import Path
from types import SimpleNamespace

import pytest
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.runtime import ChatBlocked, ChatReply
from agent_services.evals import run_answer_eval as runner
from agent_services.evals.run_answer_eval import (
    GoldenCase,
    load_cases,
    load_corpus,
    load_skill_cases,
    render_markdown,
    run_answer_eval,
)


def _text_model(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
    return ModelResponse(parts=[TextPart("占位回答。")])


def _row(report: dict[str, object], case_id: str) -> dict[str, object]:
    rows = report["cases"]
    assert isinstance(rows, list)
    for row in rows:
        if isinstance(row, dict) and row.get("id") == case_id:
            return row
    raise AssertionError(f"row {case_id} missing")


# ---------------------------------------------------------------------------
# UoW-1：断点续跑
# ---------------------------------------------------------------------------

_CASE_A = GoldenCase(
    id="resume-a",
    question="北窗咖啡每周哪一天公休？",
    expected_sources=("cafe-hours",),
    expected_keywords=("周二", "公休"),
    category="hours",
)
_CASE_B = GoldenCase(
    id="resume-b",
    question="焦糖海盐可颂的售价是多少？",
    expected_sources=("cafe-menu",),
    expected_keywords=("480",),
    category="menu",
)
_CASE_C = GoldenCase(
    id="resume-c",
    question="四人以上是否必须提前预约？座位保留几分钟？",
    expected_sources=("cafe-reservation",),
    expected_keywords=("15 分钟",),
    category="reservation",
)
_RESUME_CASES = [_CASE_A, _CASE_B, _CASE_C]


async def _make_old_report(monkeypatch: pytest.MonkeyPatch) -> dict[str, object]:
    """构造一轮半成品报告：第 1 题已计分、第 2 题 blocked、第 3 题 unscored。"""

    async def flaky_run_chat(model: object, **kwargs: object) -> object:
        del model
        message = kwargs["message"]
        if message == _CASE_A.question:
            return ChatReply(
                reply="北窗咖啡每周周二公休。",
                citations=[{"sourceId": "cafe-hours"}],
            )
        if message == _CASE_B.question:
            return ChatBlocked(
                message="AI provider call failed",
                error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            )
        raise RuntimeError("boom")

    monkeypatch.setattr(runner, "run_chat", flaky_run_chat)
    result = await run_answer_eval(
        FunctionModel(_text_model),
        model_name="function:resume",
        cases=_RESUME_CASES,
        cases_file="cases.jsonl",
    )
    report = result.report
    assert _row(report, "resume-a")["scores"]["passed"] is True  # type: ignore[index]
    assert _row(report, "resume-b")["blocked"] is True
    assert _row(report, "resume-c")["unscored"] is True
    return report


def _healthy_run_chat(calls: list[str]) -> object:
    async def healthy_run_chat(model: object, **kwargs: object) -> object:
        del model
        message = kwargs["message"]
        assert isinstance(message, str)
        calls.append(message)
        if message == _CASE_B.question:
            return ChatReply(
                reply="焦糖海盐可颂售价 480 日元。",
                citations=[{"sourceId": "cafe-menu"}],
            )
        return ChatReply(
            reply="四人以上需要提前预约，座位保留 15 分钟。",
            citations=[{"sourceId": "cafe-reservation"}],
        )

    return healthy_run_chat


async def test_resume_reuses_scored_rows_and_reruns_the_rest(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    old_report = await _make_old_report(monkeypatch)
    calls: list[str] = []
    monkeypatch.setattr(runner, "run_chat", _healthy_run_chat(calls))

    result = await run_answer_eval(
        FunctionModel(_text_model),
        model_name="function:resume",
        cases=_RESUME_CASES,
        cases_file="cases.jsonl",
        resume_report=old_report,
        resume_from="old-report.json",
    )
    report = result.report
    # 假模型只被调到未完成的两题（blocked 与 unscored），已计分题不重跑。
    assert calls == [_CASE_B.question, _CASE_C.question]
    rows = report["cases"]
    assert isinstance(rows, list)
    assert [row["id"] for row in rows] == ["resume-a", "resume-b", "resume-c"]  # type: ignore[index]
    # 复用行与旧报告逐字一致。
    assert _row(report, "resume-a") == _row(old_report, "resume-a")
    # 重跑两题本轮已计分，不再是 blocked/unscored。
    assert _row(report, "resume-b")["scores"]["passed"] is True  # type: ignore[index]
    assert "blocked" not in _row(report, "resume-b")
    assert _row(report, "resume-c")["scores"]["passed"] is True  # type: ignore[index]
    assert "unscored" not in _row(report, "resume-c")
    # 报告顶层与 Markdown 头同步标注。
    assert report["resumedFrom"] == "old-report.json"
    assert report["resumedCount"] == 1
    markdown = render_markdown(report)
    assert "Resumed from: `old-report.json`" in markdown


async def test_resume_pace_counts_only_executed_cases(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    old_report = await _make_old_report(monkeypatch)
    calls: list[str] = []
    monkeypatch.setattr(runner, "run_chat", _healthy_run_chat(calls))
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    monkeypatch.setattr(asyncio, "sleep", fake_sleep)
    await run_answer_eval(
        FunctionModel(_text_model),
        model_name="function:resume",
        cases=_RESUME_CASES,
        cases_file="cases.jsonl",
        pace_seconds=0.25,
        resume_report=old_report,
        resume_from="old-report.json",
    )
    # 实际执行 2 题（B、C）→ 只在第 2 个实际执行题之前睡 1 次；
    # 按题序计（旧口径）会睡 2 次，复用题不占执行序。
    assert sleeps == [0.25]


async def test_resume_fingerprint_mismatch_is_rejected(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    old_report = await _make_old_report(monkeypatch)

    tampered_corpus = copy.deepcopy(old_report)
    fingerprint = tampered_corpus["fingerprint"]
    assert isinstance(fingerprint, dict)
    corpus_fp = fingerprint["corpus"]
    assert isinstance(corpus_fp, dict)
    corpus_fp["sha256"] = "0" * 64
    with pytest.raises(ValueError, match="corpus"):
        await run_answer_eval(
            FunctionModel(_text_model),
            model_name="function:resume",
            cases=_RESUME_CASES,
            cases_file="cases.jsonl",
            resume_report=tampered_corpus,
            resume_from="old-report.json",
        )

    tampered_model = copy.deepcopy(old_report)
    fingerprint = tampered_model["fingerprint"]
    assert isinstance(fingerprint, dict)
    fingerprint["model"] = "function:other"
    with pytest.raises(ValueError, match="model"):
        await run_answer_eval(
            FunctionModel(_text_model),
            model_name="function:resume",
            cases=_RESUME_CASES,
            cases_file="cases.jsonl",
            resume_report=tampered_model,
            resume_from="old-report.json",
        )

    no_fingerprint = copy.deepcopy(old_report)
    del no_fingerprint["fingerprint"]
    with pytest.raises(ValueError, match="fingerprint"):
        await run_answer_eval(
            FunctionModel(_text_model),
            model_name="function:resume",
            cases=_RESUME_CASES,
            cases_file="cases.jsonl",
            resume_report=no_fingerprint,
            resume_from="old-report.json",
        )


def test_cli_resume_from_argument_parsing() -> None:
    parser = runner._parser()
    assert parser.parse_args([]).resume_from is None
    assert parser.parse_args(["--resume-from", "x.json"]).resume_from == Path("x.json")
    # 与 --baseline 可同时给，互不冲突。
    args = parser.parse_args(["--resume-from", "x.json", "--baseline", "y.json"])
    assert args.resume_from == Path("x.json")
    assert args.baseline == Path("y.json")


def test_cli_resume_from_missing_or_broken_file_errors(
    capsys: pytest.CaptureFixture[str], tmp_path: Path
) -> None:
    assert runner.main(["--resume-from", str(tmp_path / "missing.json")]) == 2
    assert "ERROR" in capsys.readouterr().err

    broken = tmp_path / "broken.json"
    broken.write_text("{not json", encoding="utf-8")
    assert runner.main(["--resume-from", str(broken)]) == 2
    assert "ERROR" in capsys.readouterr().err


def test_cli_resume_fingerprint_mismatch_exits_2(
    capsys: pytest.CaptureFixture[str],
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    old = tmp_path / "old.json"
    old.write_text(
        json.dumps(
            {
                "model": "function:tampered",
                "generatedAt": "2026-11-05T00:00:00+00:00",
                "cases": [],
                "fingerprint": {"model": "function:tampered"},
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(
        runner,
        "Settings",
        lambda: SimpleNamespace(
            agent_skills_dir="",
            agent_skills_max_instruction_chars=12000,
            agent_skills_self_select_enabled=False,
        ),
    )
    monkeypatch.setattr(
        runner,
        "configured_model_block",
        lambda settings: (FunctionModel(_text_model), "function:resume", ""),
    )
    assert runner.main(["--resume-from", str(old)]) == 2
    err = capsys.readouterr().err
    assert "ERROR" in err
    assert "fingerprint mismatch" in err


# ---------------------------------------------------------------------------
# UoW-2：真冲突文档对与 conf 正例锚点
# ---------------------------------------------------------------------------


def test_conflict_pair_documents_hold_the_two_versions() -> None:
    corpus = {doc.source_id: doc for doc in load_corpus()}
    version_2025 = corpus["supplier-hours-2025"]
    assert "周六 09:00–13:00 营业" in version_2025.content
    assert "周日休息" in version_2025.content
    version_2026 = corpus["supplier-hours-2026"]
    assert "2026 年 4 月 1 日起" in version_2026.content
    assert "周六休息" in version_2026.content
    assert "取代 2025 年版" in version_2026.content


def test_conf_supplier_hours_case_is_anchored_on_both_versions() -> None:
    case = {item.id: item for item in load_skill_cases()}["conf-pos-supplier-hours"]
    assert case.skill == "multi-source-conflict"
    assert case.kind == "positive"
    assert case.expected_sources == ("supplier-hours-2025", "supplier-hours-2026")
    assert {"周六", "2026", "休息"} <= set(case.expected_keywords)
    assert "2025" in case.expected_behavior
    assert "2026" in case.expected_behavior
    assert "为准" in case.expected_behavior
    assert "2026" in case.expected_answer


# ---------------------------------------------------------------------------
# UoW-3：长文档与长文题
# ---------------------------------------------------------------------------


def test_roast_guide_is_long_multi_paragraph_with_fact_in_last_paragraph() -> None:
    corpus = {doc.source_id: doc for doc in load_corpus()}
    guide = corpus["cafe-roast-guide"]
    assert 500 <= len(guide.content) <= 700
    paragraphs = guide.content.split("\n\n")
    assert len(paragraphs) >= 4
    # 目标事实的数字 21 只出现在末段（测长文后段召回），正文别处不出现。
    assert guide.content.count("21") == 1
    assert "21 天内用完" in paragraphs[-1]
    assert all("21" not in paragraph for paragraph in paragraphs[:-1])
    # 主题不与既有锚点交叠：营业时间/公休/价格/保固/工资一律不提。
    for anchor_word in ("营业时间", "公休", "售价", "保固", "工资"):
        assert anchor_word not in guide.content


def test_long_case_anchors_on_roast_guide_last_paragraph() -> None:
    case = {item.id: item for item in load_cases()}["long-roast-shelf-life"]
    assert case.category == "long"
    assert case.expected_sources == ("cafe-roast-guide",)
    assert "21 天" in case.expected_keywords


# ---------------------------------------------------------------------------
# UoW-4：manualOnly 单列
# ---------------------------------------------------------------------------


async def test_manual_only_cases_listed_but_stay_in_aggregate(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    golden_case = {item.id: item for item in load_cases()}["zh-proper-hours"]

    async def fake_run_chat(model: object, **kwargs: object) -> object:
        del model
        if kwargs["message"] == golden_case.question:
            return ChatReply(
                reply="北窗咖啡每周周二公休。",
                citations=[{"sourceId": "cafe-hours"}],
            )
        # 纯行为题：无 citations、无锚点 → 四指标全 None。
        return ChatReply(reply="考勤表里勤務コード 40 的记录共有 3 条。", citations=None)

    monkeypatch.setattr(runner, "run_chat", fake_run_chat)
    skill_case = {item.id: item for item in load_skill_cases()}["tbl-pos-count-rows"]
    result = await run_answer_eval(
        FunctionModel(_text_model),
        model_name="function:batch2",
        cases=[skill_case, golden_case],
    )
    report = result.report
    summary = report["summary"]
    assert isinstance(summary, dict)
    # 纯行为题仍留在聚合分母内（overall count 含它、passed 均值含其 False）。
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 2
    assert overall["passed"] == 0.5
    assert summary["manualOnlyCount"] == 1
    assert summary["manualOnlyCases"] == [
        {"id": "tbl-pos-count-rows", "category": "skill"}
    ]
    markdown = render_markdown(report)
    assert "## Manual-only cases" in markdown
    assert "tbl-pos-count-rows" in markdown


async def test_manual_only_excludes_blocked_rows(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def mixed_run_chat(model: object, **kwargs: object) -> object:
        del model
        message = kwargs["message"]
        if message == {item.id: item for item in load_cases()}["zh-proper-hours"].question:
            return ChatBlocked(
                message="AI provider call failed",
                error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            )
        return ChatReply(reply="考勤表记录共 3 条。", citations=None)

    monkeypatch.setattr(runner, "run_chat", mixed_run_chat)
    skill_case = {item.id: item for item in load_skill_cases()}["tbl-pos-count-rows"]
    golden_case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(_text_model),
        model_name="function:batch2",
        cases=[golden_case, skill_case],
    )
    summary = result.report["summary"]
    assert isinstance(summary, dict)
    assert summary["blockedCount"] == 1
    # blocked 行已有自己的单列，不被 manualOnly 重复收录。
    assert summary["manualOnlyCases"] == [
        {"id": "tbl-pos-count-rows", "category": "skill"}
    ]
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 1
