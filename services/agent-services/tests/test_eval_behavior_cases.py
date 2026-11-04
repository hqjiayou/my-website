"""#190：行为判分（behavior_checks / citation 警告）与题集扩容（多轮 history / 经验敏感题）。

行为检查与警告的口径见 evals/scoring.py 与 evals/README.md；多轮题的
history 形状与 run_chat 的 history 入参一致（ChatHistoryMessage）。
"""

import json

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

from agent_services.evals.run_answer_eval import (
    CorpusDoc,
    GoldenCase,
    evals_root,
    load_cases,
    load_corpus,
    load_lessons_file,
    run_answer_eval,
    validate_golden,
)
from agent_services.evals.scoring import (
    BEHAVIOR_CHECK_NAMES,
    case_warnings,
    check_all_expected_sources_cited,
    check_no_clarifying_question,
    evaluate_behavior_checks,
    score_case,
)

# ---------------------------------------------------------------------------
# no_clarifying_question：中英日正反例（定死模式表的回归锚点）
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "answer",
    [
        # 陈述收尾：不算反问（True = 通过检查）。
        "2026年2月最低，实发 159,366 日元。",
        "实发合计 659,293 日元（1月 163,527、2月 159,366、3月 161,000、4月 175,400）。",
        "The lowest net pay is in February 2026, at 159,366 yen.",
        "最も低いのは2026年2月で、実発は159,366円です。",
        # 问句出现在正文中间、最后以陈述收尾：不算反问收尾。
        "先确认口径：您问的是实发。结论：2026年2月最低，实发 159,366 日元。",
    ],
)
def test_no_clarifying_question_accepts_statement_endings(answer: str) -> None:
    assert check_no_clarifying_question(answer) is True


@pytest.mark.parametrize(
    "answer",
    [
        # 以反问/澄清提问收尾（False = 未通过检查）。
        "您问的是税前还是实发金额？",
        "请问您指的是哪个月的工资单？",
        "你指的是2026年的工资单吗？",
        "Do you mean gross or net pay?",
        "Which month's payslip should I compare?",
        "税前と実発のどちらですか？",
        "ご希望の口径を教えてください。",
        # 前文有结论、最后仍落回澄清提问：算反问收尾。
        "四个月的数值已列出。您问的是税前还是实发？",
    ],
)
def test_no_clarifying_question_rejects_clarifying_endings(answer: str) -> None:
    assert check_no_clarifying_question(answer) is False


def test_no_clarifying_question_blank_answer_is_none() -> None:
    assert check_no_clarifying_question("") is None
    assert check_no_clarifying_question("   ") is None


# ---------------------------------------------------------------------------
# all_expected_sources_cited：只看 citations 本身，区别于 retrieval_hit 的并集
# ---------------------------------------------------------------------------


def test_all_expected_sources_cited_cases() -> None:
    assert check_all_expected_sources_cited(["a", "b"], ["b", "a", "c"]) is True
    assert check_all_expected_sources_cited(["a", "b"], ["a"]) is False
    assert check_all_expected_sources_cited(["a"], None) is False
    assert check_all_expected_sources_cited(["a"], []) is False
    assert check_all_expected_sources_cited([], ["a"]) is None
    assert check_all_expected_sources_cited([], None) is None


def test_evaluate_behavior_checks_rejects_unknown_names() -> None:
    with pytest.raises(ValueError, match="unknown behavior check"):
        evaluate_behavior_checks(
            ["bogus"], answer="x", expected_sources=[], citation_source_ids=None
        )


# ---------------------------------------------------------------------------
# behavior_checks 计入 case pass（经 score_case）
# ---------------------------------------------------------------------------

_BASE_SCORE_INPUTS = {
    "category": "collection",
    "expected_sources": ["a", "b"],
    "expected_keywords": ["数值"],
    "observed_sources": ["a", "b"],
    "citation_source_ids": ["a", "b"],
    "answer": "数值已逐份列出，结论明确。",
}


def test_score_case_without_behavior_checks_unchanged() -> None:
    scores = score_case(**_BASE_SCORE_INPUTS)
    assert scores["passed"] is True
    assert set(scores) == {
        "retrieval_hit",
        "citation_precision",
        "keyword_coverage",
        "refusal_correct",
        "passed",
    }


def test_score_case_behavior_failure_blocks_pass() -> None:
    # 检索并集命中（observed 含 a、b），但 citations 只引了 a：
    # retrieval_hit 为真、all_expected_sources_cited 为假 → 不 pass。
    scores = score_case(
        **{**_BASE_SCORE_INPUTS, "citation_source_ids": ["a"]},
        behavior_checks=["all_expected_sources_cited"],
    )
    assert scores["retrieval_hit"] is True
    assert scores["passed"] is False
    # 同一题声明检查且 citations 齐全时 pass。
    scores = score_case(
        **_BASE_SCORE_INPUTS, behavior_checks=["all_expected_sources_cited"]
    )
    assert scores["passed"] is True
    # 反问收尾同样阻断 pass（指标全过也不行）。
    scores = score_case(
        **{**_BASE_SCORE_INPUTS, "answer": "数值已列出。请问您问的是哪一份？"},
        behavior_checks=["no_clarifying_question"],
    )
    assert scores["keyword_coverage"] == 1.0
    assert scores["passed"] is False


def test_score_case_behavior_counts_for_unanswerable_too() -> None:
    base = {
        "category": "unanswerable",
        "expected_sources": [],
        "expected_keywords": [],
        "observed_sources": [],
        "citation_source_ids": None,
    }
    scores = score_case(
        **base, answer="无法确认。", behavior_checks=["no_clarifying_question"]
    )
    assert scores["refusal_correct"] is True
    assert scores["passed"] is True
    scores = score_case(
        **base,
        answer="无法确认。您问的是哪一类文档？",
        behavior_checks=["no_clarifying_question"],
    )
    assert scores["refusal_correct"] is True
    assert scores["passed"] is False


# ---------------------------------------------------------------------------
# citation_precision 警告级：只提示、不进 pass
# ---------------------------------------------------------------------------


def test_case_warnings_threshold() -> None:
    assert case_warnings(citation_precision=None) == []
    assert case_warnings(citation_precision=1.0) == []
    assert case_warnings(citation_precision=0.5) == []
    assert case_warnings(citation_precision=0.499) == ["low_citation_precision"]
    assert case_warnings(citation_precision=0.0) == ["low_citation_precision"]


# ---------------------------------------------------------------------------
# golden 集合级题的行为检查声明：按 expected_behavior 原文对应补，不扩大
# ---------------------------------------------------------------------------


def test_golden_collection_cases_declare_matching_behavior_checks() -> None:
    cases = {case.id: case for case in load_cases()}
    assert set(cases["coll-payslip-lowest-net"].behavior_checks) == {
        "no_clarifying_question",
        "all_expected_sources_cited",
    }
    for case_id in (
        "coll-payslip-highest-net",
        "coll-payslip-total-net",
        "coll-payslip-lowest-gross",
        "coll-invoice-highest",
        "mt-payslip-range-diff",
    ):
        assert set(cases[case_id].behavior_checks) == {"all_expected_sources_cited"}, (
            case_id
        )
    # coll-rent-unanswerable 的 expected_behavior 是「不得编造/冒充」，
    # 两项检查都无对应项，不补声明。
    assert cases["coll-rent-unanswerable"].behavior_checks == ()
    for case in cases.values():
        assert set(case.behavior_checks) <= set(BEHAVIOR_CHECK_NAMES), case.id


def test_validate_rejects_unknown_behavior_check() -> None:
    corpus = [CorpusDoc(source_id="s", title="t", content="k")]
    case = GoldenCase(
        id="bad-check",
        question="q?",
        expected_sources=("s",),
        expected_keywords=("k",),
        category="hours",
        behavior_checks=("bogus",),
    )
    with pytest.raises(ValueError, match="unknown behavior checks"):
        validate_golden(corpus, [case])


# ---------------------------------------------------------------------------
# 多轮题：history 解析、轮次校验、派生值复算留痕
# ---------------------------------------------------------------------------

MT_IDS = (
    "mt-hours-weekend",
    "mt-warranty-after-burr",
    "mt-menu-seasonal",
    "mt-reservation-hold",
    "mt-payslip-range-diff",
)


def test_multi_turn_cases_parse_history() -> None:
    cases = {case.id: case for case in load_cases()}
    for case_id in MT_IDS:
        case = cases[case_id]
        assert len(case.history) == 2, case_id
        assert [item.role for item in case.history] == ["USER", "ASSISTANT"], case_id
    assert sum(1 for case in cases.values() if case.history) == len(MT_IDS)


def test_multi_turn_derived_value_recomputed() -> None:
    """mt-payslip-range-diff 的差额是派生值：用语料原值当场复算核对。"""
    assert 175_400 - 159_366 == 16_034
    assert 163_527 + 159_366 + 161_000 + 175_400 == 659_293
    case = {item.id: item for item in load_cases()}["mt-payslip-range-diff"]
    assert "16,034" in case.expected_answer
    assert "175,400" in case.expected_answer
    assert "159,366" in case.expected_answer
    # 上一轮助手回复里的合计与四个月数值须与语料一致（承接关系的锚）。
    prior = case.history[1].content
    assert "659,293" in prior
    for token in ("163,527", "159,366", "161,000", "175,400"):
        assert token in prior


def test_history_load_and_validate_reject_bad_shapes(tmp_path) -> None:
    path = tmp_path / "cases.jsonl"
    bad_role = {
        "id": "bad-role",
        "question": "q?",
        "expected_sources": ["s"],
        "expected_keywords": ["k"],
        "category": "hours",
        "history": [{"role": "SYSTEM", "content": "x"}],
    }
    path.write_text(json.dumps(bad_role, ensure_ascii=False) + "\n", encoding="utf-8")
    with pytest.raises(ValueError, match="history"):
        load_cases(path)
    ends_with_user = {
        "id": "bad-order",
        "question": "q?",
        "expected_sources": ["s"],
        "expected_keywords": ["k"],
        "category": "hours",
        "history": [
            {"role": "USER", "content": "一问"},
            {"role": "USER", "content": "再问"},
        ],
    }
    path.write_text(
        json.dumps(ends_with_user, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    loaded = load_cases(path)
    corpus = [CorpusDoc(source_id="s", title="t", content="k")]
    with pytest.raises(ValueError, match="history"):
        validate_golden(corpus, loaded)


# ---------------------------------------------------------------------------
# 管线：history 真进 run 入参（捕获断言）；无 history 时不带
# ---------------------------------------------------------------------------


def _capture_model(seen: list[list[ModelMessage]]):
    """先调 search_site_knowledge，再把工具返回原样作答（同 smoke 口径），
    同时记录每次调用收到的 messages 供捕获断言。"""

    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(list(messages))
        question = ""
        for message in messages:
            for part in getattr(message, "parts", []):
                if (
                    isinstance(part, ToolReturnPart)
                    and part.tool_name == "search_site_knowledge"
                ):
                    content = part.content if isinstance(part.content, str) else ""
                    if not content or content.startswith("No matching"):
                        return ModelResponse(
                            parts=[TextPart("无法确认。没有相关信息。")]
                        )
                    return ModelResponse(parts=[TextPart(content)])
                if isinstance(part, UserPromptPart) and isinstance(part.content, str):
                    question = part.content
        return ModelResponse(
            parts=[ToolCallPart("search_site_knowledge", {"query": question})]
        )

    return FunctionModel(respond)


def _message_texts(messages: list[ModelMessage]) -> list[str]:
    texts: list[str] = []
    for message in messages:
        for part in getattr(message, "parts", []):
            if isinstance(part, (UserPromptPart, TextPart)) and isinstance(
                part.content, str
            ):
                texts.append(part.content)
    return texts


async def test_history_reaches_run_inputs_and_report_row() -> None:
    by_id = {case.id: case for case in load_cases()}
    case = by_id["mt-warranty-after-burr"]
    seen: list[list[ModelMessage]] = []
    result = await run_answer_eval(
        _capture_model(seen), model_name="function:capture", cases=[case]
    )
    assert seen, "model must have been called"
    first_call_texts = _message_texts(seen[0])
    # 先前轮次的问与答都进了模型入参（message_history），且本轮问题也在。
    assert "XJ-900 的刀盘直径是多少？" in first_call_texts
    assert "XJ-900 的刀盘直径是 48 mm。" in first_call_texts
    row = result.report["cases"][0]
    assert row["history"] == [
        {"role": "USER", "content": "XJ-900 的刀盘直径是多少？"},
        {"role": "ASSISTANT", "content": "XJ-900 的刀盘直径是 48 mm。"},
    ]


async def test_no_history_case_sends_no_history() -> None:
    by_id = {case.id: case for case in load_cases()}
    case = by_id["model-xj900"]
    seen: list[list[ModelMessage]] = []
    result = await run_answer_eval(
        _capture_model(seen), model_name="function:capture", cases=[case]
    )
    all_texts = [text for call in seen for text in _message_texts(call)]
    assert "XJ-900 的刀盘直径是 48 mm。" not in all_texts
    row = result.report["cases"][0]
    assert "history" not in row


# ---------------------------------------------------------------------------
# 管线：warnings 进报告行与汇总，且不影响 pass
# ---------------------------------------------------------------------------


async def test_low_citation_precision_warns_without_failing(tmp_path) -> None:
    # 三份都是「营业时间」主题：b/c 与查询词高度重合会被一并检回并进入
    # citations，期望来源只有 a → citation_precision = 1/3 < 0.5。
    corpus = [
        CorpusDoc(
            source_id="warn-a",
            title="Alpha main hours",
            content="alpha cafe main branch opening hours 08:30 weekdays.",
        ),
        CorpusDoc(
            source_id="warn-b",
            title="Alpha north hours",
            content="alpha cafe north branch opening hours 09:30 saturdays.",
        ),
        CorpusDoc(
            source_id="warn-c",
            title="Alpha south hours",
            content="alpha cafe south branch opening hours 10:30 sundays.",
        ),
    ]
    case = GoldenCase(
        id="warn-case",
        question="alpha cafe opening hours?",
        expected_sources=("warn-a",),
        expected_keywords=("08:30",),
        category="hours",
    )
    seen: list[list[ModelMessage]] = []
    result = await run_answer_eval(
        _capture_model(seen),
        model_name="function:warn",
        corpus=corpus,
        cases=[case],
        out=tmp_path,
    )
    row = result.report["cases"][0]
    assert row["scores"]["citation_precision"] is not None
    assert row["scores"]["citation_precision"] < 0.5
    assert row["warnings"] == ["low_citation_precision"]
    # 警告不进 pass：指标全过时照样 pass。
    assert row["scores"]["passed"] is True
    summary = result.report["summary"]
    assert summary["warningCount"] == 1
    assert summary["warningCases"] == [
        {"id": "warn-case", "category": "hours", "warnings": ["low_citation_precision"]}
    ]
    assert result.markdown_path is not None
    markdown = result.markdown_path.read_text(encoding="utf-8")
    assert "## Warnings" in markdown
    assert "warn-case" in markdown


# ---------------------------------------------------------------------------
# 经验敏感题：题集自洽＋样例 lessons 可加载（两轮对照的素材）
# ---------------------------------------------------------------------------


def test_experience_set_validates_and_lessons_load() -> None:
    corpus = load_corpus()
    cases = load_cases(evals_root() / "experience_qa.jsonl")
    validate_golden(corpus, cases)
    assert [case.id for case in cases] == [
        "exp-payslip-lowest",
        "exp-hours-both-periods",
        "exp-invoice-highest-listed",
    ]
    for case in cases:
        assert case.behavior_checks, case.id
        assert case.expected_behavior, case.id
    golden_ids = {case.id for case in load_cases()}
    assert golden_ids.isdisjoint(case.id for case in cases)
    lessons = load_lessons_file(evals_root() / "fixtures" / "lessons_sample.json")
    assert [item.lesson_no for item in lessons] == [6, 7, 8]
    assert all(item.lesson.strip() for item in lessons)
