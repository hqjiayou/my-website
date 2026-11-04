"""#187 UoW-1：eval runner 注入接通的确定性验收（假模型层）。

覆盖四参数的注入生效与缺省回归：--cases（skill 题集加载/派生/分派）、
--skills（生产同一 resolve_skills 路径注入、逐题 triggered/completed）、
--harness-dir（组装指向变体目录）、--lessons-file（经验条目注入与坏文件报错）。
真模型触发率/完成率数字不在 CI 内跑（沙箱无 AI_MODEL），留 UoW-3 本机读数。
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from pydantic_ai.messages import (
    ModelMessage,
    ModelResponse,
    SystemPromptPart,
    TextPart,
    ToolCallPart,
    ToolReturnPart,
    UserPromptPart,
)
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.evals.run_answer_eval import (
    SKILL_FALLBACK_CATEGORY,
    GoldenCase,
    evals_root,
    load_any_cases,
    load_cases,
    load_corpus,
    load_lessons_file,
    load_skill_cases,
    main,
    render_markdown,
    run_answer_eval,
    validate_eval_cases,
)
from agent_services.evals.scoring import case_passed
from agent_services.schemas.chat import ExperienceLessonItem

# ---------------------------------------------------------------------------
# 题集加载与判分口径（纯函数层）
# ---------------------------------------------------------------------------


def test_load_skill_cases_derives_anchor_fields() -> None:
    golden = {case.id: case for case in load_cases()}
    cases = {case.id: case for case in load_skill_cases()}
    assert len(cases) == 22
    assert all(case.skill for case in cases.values())

    anchored = cases["agg-pos-lowest-net"]
    anchor = golden["coll-payslip-lowest-net"]
    assert anchored.category == anchor.category == "collection"
    assert anchored.expected_sources == anchor.expected_sources
    # 题面自带关键词时以题面为准，不回退黄金集。
    assert anchored.expected_keywords == ("2026年2月", "159,366", "实发")

    refusal = cases["qa-neg-not-covered"]
    assert refusal.category == "unanswerable"
    assert refusal.expected_sources == ()
    assert refusal.expected_keywords == ()

    keywords_only = cases["agg-pos-invoice-total"]
    assert keywords_only.category == SKILL_FALLBACK_CATEGORY
    assert keywords_only.expected_sources == ()
    assert keywords_only.expected_keywords == ("37,600",)

    behavior_only = cases["tbl-pos-count-rows"]
    assert behavior_only.category == SKILL_FALLBACK_CATEGORY
    assert behavior_only.expected_sources == ()
    assert behavior_only.expected_keywords == ()


def test_load_skill_cases_unknown_ref_is_rejected(tmp_path: Path) -> None:
    path = tmp_path / "skill.jsonl"
    path.write_text(
        json.dumps(
            {
                "id": "x-1",
                "skill": "doc-qa-cited",
                "kind": "positive",
                "question": "q?",
                "golden_ref": "no-such-case",
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="unknown golden case"):
        load_skill_cases(path)


def test_load_any_cases_dispatches_by_shape(tmp_path: Path) -> None:
    skill_cases = load_any_cases(evals_root() / "skill_qa.jsonl")
    assert len(skill_cases) == 22
    assert all(case.skill for case in skill_cases)

    golden_path = tmp_path / "golden.jsonl"
    golden_path.write_text(
        json.dumps(
            {
                "id": "g-1",
                "question": "q?",
                "expected_sources": ["s"],
                "expected_keywords": ["k"],
                "category": "hours",
            }
        )
        + "\n",
        encoding="utf-8",
    )
    golden_cases = load_any_cases(golden_path)
    assert [case.id for case in golden_cases] == ["g-1"]
    assert golden_cases[0].skill == ""


def test_validate_eval_cases_accepts_real_skill_set_and_rejects_duplicates() -> None:
    corpus = load_corpus()
    validate_eval_cases(corpus, load_skill_cases())

    case = load_skill_cases()[0]
    with pytest.raises(ValueError, match="duplicate case id"):
        validate_eval_cases(corpus, [case, case])


def test_case_passed_scores_whichever_metrics_exist() -> None:
    # 只有关键词可用（skill 关键词题）：按覆盖率判。
    assert (
        case_passed(
            "skill", retrieval_hit=None, keyword_coverage=1.0, refusal_correct=None
        )
        is True
    )
    assert (
        case_passed(
            "skill", retrieval_hit=None, keyword_coverage=0.49, refusal_correct=None
        )
        is False
    )
    # 只有来源可用：按命中判。
    assert (
        case_passed(
            "skill", retrieval_hit=True, keyword_coverage=None, refusal_correct=None
        )
        is True
    )
    assert (
        case_passed(
            "skill", retrieval_hit=False, keyword_coverage=None, refusal_correct=None
        )
        is False
    )
    # 两样都缺（纯行为题）：无机器锚点，不 pass。
    assert (
        case_passed(
            "skill", retrieval_hit=None, keyword_coverage=None, refusal_correct=None
        )
        is False
    )


def test_load_lessons_file_roundtrip_and_rejections(tmp_path: Path) -> None:
    good = tmp_path / "lessons.json"
    good.write_text(
        json.dumps(
            [{"lessonNo": 6, "lesson": "先枚举再逐份比较。"}], ensure_ascii=False
        ),
        encoding="utf-8",
    )
    lessons = load_lessons_file(good)
    assert lessons == [ExperienceLessonItem(lessonNo=6, lesson="先枚举再逐份比较。")]

    empty = tmp_path / "empty.json"
    empty.write_text("[]", encoding="utf-8")
    assert load_lessons_file(empty) == []

    with pytest.raises(ValueError, match="cannot be read"):
        load_lessons_file(tmp_path / "missing.json")

    broken = tmp_path / "broken.json"
    broken.write_text("{not json", encoding="utf-8")
    with pytest.raises(ValueError, match="not valid JSON"):
        load_lessons_file(broken)

    not_array = tmp_path / "object.json"
    not_array.write_text('{"lessonNo": 6, "lesson": "x"}', encoding="utf-8")
    with pytest.raises(ValueError, match="must be a JSON array"):
        load_lessons_file(not_array)

    for payload in (
        '[{"lesson": "缺编号"}]',
        '[{"lessonNo": 0, "lesson": "编号从 1 起"}]',
        "[42]",
    ):
        bad_item = tmp_path / "bad-item.json"
        bad_item.write_text(payload, encoding="utf-8")
        with pytest.raises(ValueError, match="item 0"):
            load_lessons_file(bad_item)


# ---------------------------------------------------------------------------
# 管线层（FunctionModel）：注入生效与缺省回归
# ---------------------------------------------------------------------------


class _Recorder:
    """与 test_answer_eval 的 smoke 同路径：先检索、再据工具返回作答；顺带记录系统提示。"""

    def __init__(self) -> None:
        self.system_prompts: list[str] = []

    def respond(self, messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        question = ""
        for message in messages:
            for part in getattr(message, "parts", []):
                if isinstance(part, SystemPromptPart):
                    self.system_prompts.append(part.content)
                elif (
                    isinstance(part, ToolReturnPart)
                    and part.tool_name == "search_site_knowledge"
                ):
                    content = part.content if isinstance(part.content, str) else ""
                    if not content or content.startswith("No matching"):
                        return ModelResponse(
                            parts=[TextPart("无法确认。没有相关信息。")]
                        )
                    return ModelResponse(parts=[TextPart(content)])
                elif isinstance(part, UserPromptPart) and isinstance(part.content, str):
                    question = part.content
        return ModelResponse(
            parts=[ToolCallPart("search_site_knowledge", {"query": question})]
        )


def _row(report: dict[str, object], case_id: str) -> dict[str, object]:
    rows = report["cases"]
    assert isinstance(rows, list)
    for row in rows:
        if isinstance(row, dict) and row.get("id") == case_id:
            return row
    raise AssertionError(f"row {case_id} missing")


async def test_default_run_keeps_legacy_report_shape() -> None:
    recorder = _Recorder()
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(recorder.respond), model_name="function:wiring", cases=[case]
    )
    report = result.report
    assert "skills" not in report
    assert "harnessDir" not in report
    assert "lessonsCount" not in report
    row = _row(report, "zh-proper-hours")
    assert "triggered" not in row
    assert "completed" not in row
    assert "skillsApplied" not in row
    assert row["scores"]["passed"] is True  # type: ignore[index]
    assert "triggered" not in render_markdown(report)


async def test_base_skills_are_injected_and_annotated() -> None:
    recorder = _Recorder()
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    result = await run_answer_eval(
        FunctionModel(recorder.respond),
        model_name="function:wiring",
        cases=[case],
        skills=["orchestration"],
    )
    assert result.report["skills"] == ["orchestration"]
    assert any(
        "## Skill: orchestration" in prompt for prompt in recorder.system_prompts
    )
    # 黄金题无 skill 字段：逐题行不带触发/完成标注（不适用，不报错）。
    row = _row(result.report, "zh-proper-hours")
    assert "triggered" not in row


async def test_skill_cases_report_triggered_and_completed() -> None:
    recorder = _Recorder()
    wanted = ("agg-pos-lowest-net", "qa-neg-not-covered")
    by_id = {case.id: case for case in load_skill_cases()}
    result = await run_answer_eval(
        FunctionModel(recorder.respond),
        model_name="function:wiring",
        cases=[by_id[case_id] for case_id in wanted],
    )
    assert any("## Skill: cross-doc-aggregation" in p for p in recorder.system_prompts)
    assert any("## Skill: doc-qa-cited" in p for p in recorder.system_prompts)

    agg = _row(result.report, "agg-pos-lowest-net")
    assert agg["skill"] == "cross-doc-aggregation"
    assert agg["kind"] == "positive"
    assert agg["skillsApplied"] == ["cross-doc-aggregation"]
    assert agg["triggered"] is True
    assert agg["completed"] == agg["scores"]["passed"]  # type: ignore[index]

    neg = _row(result.report, "qa-neg-not-covered")
    assert neg["skillsApplied"] == ["doc-qa-cited"]
    assert neg["triggered"] is True
    # 不可答题：假模型按检索无命中拒答，refusal_correct 成立 → 完成。
    assert neg["scores"]["refusal_correct"] is True  # type: ignore[index]
    assert neg["completed"] is True

    markdown = render_markdown(result.report)
    assert "triggered" in markdown
    assert "completed" in markdown


async def test_unknown_skill_is_missing_not_triggered() -> None:
    ghost = GoldenCase(
        id="ghost-1",
        question="营业时间是几点？",
        expected_sources=(),
        expected_keywords=(),
        category=SKILL_FALLBACK_CATEGORY,
        skill="ghost-skill",
        kind="positive",
    )
    result = await run_answer_eval(
        FunctionModel(_Recorder().respond), model_name="function:wiring", cases=[ghost]
    )
    row = _row(result.report, "ghost-1")
    assert row["skillsApplied"] == []
    assert row["skillsMissing"] == ["ghost-skill"]
    assert row["triggered"] is False
    assert row["completed"] is False


async def test_behavior_only_case_has_no_machine_pass() -> None:
    case = {item.id: item for item in load_skill_cases()}["tbl-pos-count-rows"]
    result = await run_answer_eval(
        FunctionModel(_Recorder().respond), model_name="function:wiring", cases=[case]
    )
    row = _row(result.report, "tbl-pos-count-rows")
    assert row["triggered"] is True
    scores = row["scores"]
    assert isinstance(scores, dict)
    assert scores["retrieval_hit"] is None
    assert scores["keyword_coverage"] is None
    assert scores["passed"] is False
    assert row["completed"] is False


async def test_harness_dir_switches_platform_files(tmp_path: Path) -> None:
    variant = tmp_path / "harness"
    variant.mkdir()
    (variant / "background.md").write_text("MARKER-BACKGROUND-187", encoding="utf-8")
    (variant / "guardrails.md").write_text("guardrails", encoding="utf-8")
    (variant / "tool_guidance.md").write_text("guidance", encoding="utf-8")
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]

    wired = _Recorder()
    result = await run_answer_eval(
        FunctionModel(wired.respond),
        model_name="function:wiring",
        cases=[case],
        harness_dir=str(variant),
    )
    assert any("MARKER-BACKGROUND-187" in p for p in wired.system_prompts)
    assert result.report["harnessDir"] == str(variant)

    plain = _Recorder()
    await run_answer_eval(
        FunctionModel(plain.respond), model_name="function:wiring", cases=[case]
    )
    assert not any("MARKER-BACKGROUND-187" in p for p in plain.system_prompts)


async def test_lessons_file_entries_reach_the_prompt() -> None:
    case = {item.id: item for item in load_cases()}["zh-proper-hours"]
    lessons = [ExperienceLessonItem(lessonNo=7, lesson="LESSON-MARKER-187")]

    wired = _Recorder()
    result = await run_answer_eval(
        FunctionModel(wired.respond),
        model_name="function:wiring",
        cases=[case],
        experience_lessons=lessons,
    )
    assert any("L7. LESSON-MARKER-187" in p for p in wired.system_prompts)
    assert result.report["lessonsCount"] == 1

    plain = _Recorder()
    await run_answer_eval(
        FunctionModel(plain.respond), model_name="function:wiring", cases=[case]
    )
    assert not any("LESSON-MARKER-187" in p for p in plain.system_prompts)


# ---------------------------------------------------------------------------
# CLI：坏注入文件先于跑分报错（不静默降级）
# ---------------------------------------------------------------------------


def test_cli_rejects_bad_injection_files(
    capsys: pytest.CaptureFixture[str], tmp_path: Path
) -> None:
    assert main(["--lessons-file", str(tmp_path / "missing.json")]) == 2
    assert "ERROR" in capsys.readouterr().err

    broken = tmp_path / "broken.json"
    broken.write_text("[{]", encoding="utf-8")
    assert main(["--lessons-file", str(broken)]) == 2
    assert "ERROR" in capsys.readouterr().err

    assert main(["--cases", str(tmp_path / "missing.jsonl")]) == 2
    assert "ERROR" in capsys.readouterr().err

    assert main(["--harness-dir", str(tmp_path / "no-such-dir")]) == 2
    assert "ERROR" in capsys.readouterr().err
