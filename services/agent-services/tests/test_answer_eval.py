"""答案质量黄金集：评分器单测，以及 FunctionModel 下的管线 smoke。"""

import json
from datetime import UTC, datetime

from conftest import make_settings
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
    EVAL_SITE_ID,
    build_eval_index,
    configured_model_block,
    load_cases,
    load_corpus,
    main,
    run_answer_eval,
    validate_golden,
)
from agent_services.evals.scoring import (
    aggregate,
    case_passed,
    citation_precision,
    has_refusal_cue,
    keyword_coverage,
    refusal_correct,
    retrieval_hit,
    score_case,
)
from agent_services.retrieval.service import query_terms


def test_retrieval_hit_positive_and_negative() -> None:
    assert retrieval_hit(["a", "b"], ["b", "a", "c"]) is True
    assert retrieval_hit(["a", "b"], ["a"]) is False
    assert retrieval_hit([], ["a"]) is None


def test_citation_precision_positive_and_negative() -> None:
    assert citation_precision(["a", "b"], ["a", "a", "b"]) == 1.0
    assert citation_precision(["a"], ["a", "b"]) == 0.5
    assert citation_precision(["a"], ["b"]) == 0.0
    assert citation_precision(["a"], None) is None
    assert citation_precision(["a"], []) is None


def test_keyword_coverage_positive_and_negative() -> None:
    assert keyword_coverage(["XJ-900", "48 mm"], "The xj-900 burr is 48 mm.") == 1.0
    assert keyword_coverage(["周二", "公休"], "公休尚未写明") == 0.5
    assert keyword_coverage(["周二"], "周末营业") == 0.0
    assert keyword_coverage([], "任何回答") is None


def test_refusal_correct_positive_and_negative() -> None:
    assert (
        refusal_correct(category="unanswerable", answer="无法确认。", citation_source_ids=None) is True
    )
    assert (
        refusal_correct(
            category="unanswerable", answer="沒有相關資訊", citation_source_ids=None
        )
        is True
    )
    assert (
        refusal_correct(category="unanswerable", answer="わかりません。", citation_source_ids=[])
        is True
    )
    assert (
        refusal_correct(
            category="unanswerable",
            answer="I cannot confirm the price.",
            citation_source_ids=None,
        )
        is True
    )
    assert (
        refusal_correct(
            category="unanswerable", answer="无法确认。", citation_source_ids=["cafe-hours"]
        )
        is False
    )
    assert (
        refusal_correct(category="unanswerable", answer="营业到 18:00。", citation_source_ids=None)
        is False
    )
    assert (
        refusal_correct(category="unanswerable", answer="相关信息如下：周一营业。", citation_source_ids=None)
        is False
    )
    assert refusal_correct(category="hours", answer="无法确认。", citation_source_ids=None) is None


def test_refusal_correct_matches_attested_live_replies() -> None:
    """2026-10-27 真跑分（gemini-3.5-flash）4 题不可答的实际回复原文。

    模型实质拒答、零捏造，但旧词表一条未命中、refusal_correct 全记 False。
    这组原文是回归锚点：词表再改也不许把它们漏掉。
    """
    attested = (
        "关于访客无线网络登录口令写在哪张卡片的信息，目前系统和工具数据中未查到相关说明，暂无法提供。",
        "Information about the Zephyr satellite's orbital period is not available.",
        "暂无相关数据或信息包含人工降雪机的夜间票价。",
        "抱歉，目前没有相关的工具数据或上下文信息可以确认青洲码头末班渡轮的停靠状态。建议查阅官方渡轮实时航讯或联系码头工作人员进行核实。",
    )
    for answer in attested:
        assert (
            refusal_correct(category="unanswerable", answer=answer, citation_source_ids=None)
            is True
        ), answer
    # 具体性反例：片段相近但属于正常作答，不许误判为拒答。
    assert (
        refusal_correct(category="unanswerable", answer="本店无法提供发票。", citation_source_ids=None)
        is False
    )
    assert (
        refusal_correct(category="unanswerable", answer="暂无库存，补货中。", citation_source_ids=None)
        is False
    )
    # 带 citations 时即使有拒答措辞也不算拒答（既有闸门）。
    assert (
        refusal_correct(
            category="unanswerable",
            answer=attested[1],
            citation_source_ids=["cafe-hours"],
        )
        is False
    )


def test_refusal_correct_matches_2026_10_29_ab_rerun_replies() -> None:
    """2026-10-29 A/B 补验 Arm B 原文：unans-orbit、unans-snow 的实际回复。

    两条此前都不在词表内（"unable to find" 不含 "could not find"；
    「未找到相关」因中间隔了宾语漏命「未找到……的相关信息」），
    实质正确拒答被记 refusal_correct=False。
    """
    orbit_answer = (
        "I could not find information about the Zephyr satellite's orbital "
        "period in the site's documents."
    )
    snow_answer = '在站内知识库中未找到关于"人工降雪机夜间票价"的相关信息。'
    assert has_refusal_cue(orbit_answer) is True
    assert has_refusal_cue(snow_answer) is True
    assert (
        refusal_correct(category="unanswerable", answer=orbit_answer, citation_source_ids=None)
        is True
    )
    assert (
        refusal_correct(category="unanswerable", answer=snow_answer, citation_source_ids=None)
        is True
    )
    # 纪律负例：正常作答含「无法提供」不误伤，已在上面
    # test_refusal_correct_matches_attested_live_replies 里覆盖，这里不重复加。


def test_case_passed_follows_the_written_rule() -> None:
    assert (
        case_passed("hours", retrieval_hit=True, keyword_coverage=0.5, refusal_correct=None) is True
    )
    assert (
        case_passed("hours", retrieval_hit=True, keyword_coverage=0.49, refusal_correct=None)
        is False
    )
    assert (
        case_passed("hours", retrieval_hit=False, keyword_coverage=1.0, refusal_correct=None)
        is False
    )
    assert case_passed("unanswerable", retrieval_hit=None, keyword_coverage=None, refusal_correct=True)
    assert (
        case_passed("unanswerable", retrieval_hit=None, keyword_coverage=None, refusal_correct=False)
        is False
    )


def test_aggregate_skips_missing_metrics_and_groups_categories() -> None:
    summary = aggregate(
        [
            {
                "category": "hours",
                "retrieval_hit": True,
                "citation_precision": 1.0,
                "keyword_coverage": 1.0,
                "refusal_correct": None,
                "passed": True,
            },
            {
                "category": "unanswerable",
                "retrieval_hit": None,
                "citation_precision": None,
                "keyword_coverage": None,
                "refusal_correct": False,
                "passed": False,
            },
        ]
    )
    overall = summary["overall"]
    assert overall["count"] == 2
    assert overall["retrieval_hit"] == 1.0
    assert overall["citation_precision"] == 1.0
    assert overall["refusal_correct"] == 0.0
    assert overall["passed"] == 0.5
    assert set(summary["byCategory"]) == {"hours", "unanswerable"}
    assert summary["byCategory"]["unanswerable"]["citation_precision"] is None


def test_distractors_are_valid_and_disjoint_from_corpus() -> None:
    from agent_services.evals.run_answer_eval import load_distractors

    corpus_ids = {doc.source_id for doc in load_corpus()}
    distractors = load_distractors()
    assert distractors, "distractor layer must not be empty"
    distractor_ids = {doc.source_id for doc in distractors}
    assert len(distractor_ids) == len(distractors)
    assert distractor_ids.isdisjoint(corpus_ids)
    assert all(doc.content.strip() for doc in distractors)


def test_golden_set_matches_the_corpus() -> None:
    corpus = load_corpus()
    cases = load_cases()
    validate_golden(corpus, cases)
    by_source = {doc.source_id: doc for doc in corpus}
    assert len(corpus) == 13
    assert len(cases) == 18
    assert sum(case.category == "unanswerable" for case in cases) == 5
    assert sum(case.category != "unanswerable" for case in cases) == 13
    assert sum(case.category == "collection" for case in cases) == 5
    assert any(len(case.expected_sources) == 2 for case in cases)
    assert any(case.category == "model" and "XJ-900" in case.question for case in cases)
    assert any("の" in case.question for case in cases)
    assert any("北窗咖啡" in case.question for case in cases)
    blob = "\n".join(doc.content for doc in corpus).casefold()
    for case in cases:
        if case.category == "unanswerable":
            leaked = [token for token in query_terms(case.question) if token.casefold() in blob]
            assert leaked == [], (case.id, leaked)
            continue
        expected = "\n".join(by_source[source].content for source in case.expected_sources).casefold()
        for keyword in case.expected_keywords:
            # 集合级合计类题的关键词是派生数值（如四个月实发总和），不出现在
            # 任何单份文档里，改由 expected_answer 承载并在此核对。
            assert keyword.casefold() in expected or keyword.casefold() in case.expected_answer.casefold(), (
                case.id,
                keyword,
            )


def test_collection_family_covers_the_payslip_incident() -> None:
    """#180 UoW-2：工资单原题与变体、换文档类、不存在文档类各就其位。"""
    cases = {case.id: case for case in load_cases()}
    original = cases["coll-payslip-lowest-net"]
    assert original.question == "现有工资单里哪一个月的工资最低"
    assert original.category == "collection"
    assert set(original.expected_sources) == {
        "payslip-2026-01",
        "payslip-2026-02",
        "payslip-2026-03",
        "payslip-2026-04",
    }
    for case_id in (
        "coll-payslip-lowest-net",
        "coll-payslip-highest-net",
        "coll-payslip-total-net",
        "coll-payslip-lowest-gross",
        "coll-invoice-highest",
    ):
        case = cases[case_id]
        assert case.category == "collection", case_id
        assert "枚举" in case.expected_behavior, case_id
        assert case.expected_answer, case_id
    # 税前口径题的期望答案必须与实发口径不同月，否则口径变体没有区分度。
    assert "2026年3月" in cases["coll-payslip-lowest-gross"].expected_answer
    assert "2026年2月" in cases["coll-payslip-lowest-net"].expected_answer
    missing_class = cases["coll-rent-unanswerable"]
    assert missing_class.category == "unanswerable"
    assert "租赁合同" in missing_class.expected_behavior


def test_optional_case_fields_default_empty_and_parse_when_present(tmp_path) -> None:
    path = tmp_path / "golden.jsonl"
    path.write_text(
        json.dumps(
            {"id": "plain", "question": "q?", "expected_sources": ["s"], "expected_keywords": ["k"], "category": "hours"},
            ensure_ascii=False,
        )
        + "\n"
        + json.dumps(
            {
                "id": "annotated",
                "question": "q2?",
                "expected_sources": ["s"],
                "expected_keywords": ["k"],
                "category": "collection",
                "expected_behavior": "枚举全集后比较。",
                "expected_answer": "派生答案。",
            },
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    plain, annotated = load_cases(path)
    assert plain.expected_behavior == "" and plain.expected_answer == ""
    assert annotated.expected_behavior == "枚举全集后比较。"
    assert annotated.expected_answer == "派生答案。"


def test_collection_case_without_behavior_is_rejected() -> None:
    from agent_services.evals.run_answer_eval import CorpusDoc, GoldenCase

    corpus = [CorpusDoc(source_id="s", title="t", content="k")]
    case = GoldenCase(
        id="coll-bad",
        question="q?",
        expected_sources=("s",),
        expected_keywords=("k",),
        category="collection",
    )
    try:
        validate_golden(corpus, [case])
    except ValueError as exc:
        assert "expected_behavior" in str(exc)
    else:
        raise AssertionError("collection case without expected_behavior must be rejected")


async def test_golden_retrieval_hits_expected_sources_only() -> None:
    corpus = load_corpus()
    cases = load_cases()
    service = await build_eval_index(corpus)
    try:
        for case in cases:
            chunks = await service.retrieve_chunks(agent_id=EVAL_SITE_ID, query=case.question)
            found = {chunk.source_id for chunk in chunks}
            if case.category == "unanswerable":
                assert found == set(), (case.id, found)
            else:
                assert set(case.expected_sources) <= found, (case.id, found)
    finally:
        await service.close()


def _smoke_respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
    """模拟真实模型的工具路径：先调 search_site_knowledge，再据工具返回作答。"""
    question = ""
    for message in messages:
        for part in getattr(message, "parts", []):
            if isinstance(part, ToolReturnPart) and part.tool_name == "search_site_knowledge":
                content = part.content if isinstance(part.content, str) else ""
                if not content or content.startswith("No matching"):
                    return ModelResponse(parts=[TextPart("无法确认。没有相关信息。")])
                return ModelResponse(parts=[TextPart(content)])
            if isinstance(part, UserPromptPart) and isinstance(part.content, str):
                question = part.content
    return ModelResponse(
        parts=[ToolCallPart("search_site_knowledge", {"query": question})]
    )


async def test_pipeline_smoke_scores_three_mini_cases(tmp_path) -> None:
    wanted = ("zh-proper-hours", "unans-orbit", "model-xj900")
    by_id = {case.id: case for case in load_cases()}
    cases = [by_id[case_id] for case_id in wanted]
    when = datetime(2026, 10, 26, 3, 27, tzinfo=UTC)
    result = await run_answer_eval(
        FunctionModel(_smoke_respond),
        model_name="function:smoke",
        cases=cases,
        out=tmp_path,
        generated_at=when,
    )
    report = result.report
    assert report["model"] == "function:smoke"
    assert report["generatedAt"] == when.isoformat()
    assert "keyword_coverage ≥ 0.5" in str(report["passRule"])
    assert "refusal_correct" in str(report["passRule"])
    rows = report["cases"]
    assert isinstance(rows, list)
    assert [row["id"] for row in rows] == list(wanted)
    summary = report["summary"]
    assert isinstance(summary, dict)
    assert summary["overall"]["count"] == 3
    assert set(summary["byCategory"]) == {"hours", "unanswerable", "model"}
    for row in rows:
        scores = row["scores"]
        assert set(scores) == {
            "retrieval_hit",
            "citation_precision",
            "keyword_coverage",
            "refusal_correct",
            "passed",
        }
        assert scores == score_case(
            category=row["category"],
            expected_sources=row["expectedSources"],
            expected_keywords=by_id[row["id"]].expected_keywords,
            observed_sources=[item["sourceId"] for item in row["retrieved"]]
            + [item["sourceId"] for item in (row["citations"] or [])],
            citation_source_ids=[item["sourceId"] for item in row["citations"]]
            if row["citations"]
            else None,
            answer=row["reply"],
        )
        assert scores["passed"] is case_passed(
            row["category"],
            retrieval_hit=scores["retrieval_hit"],
            keyword_coverage=scores["keyword_coverage"],
            refusal_correct=scores["refusal_correct"],
        )
    scored = {row["id"]: row["scores"] for row in rows}
    assert scored["zh-proper-hours"]["retrieval_hit"] is True
    assert scored["zh-proper-hours"]["keyword_coverage"] >= 0.5
    assert scored["zh-proper-hours"]["passed"] is True
    assert scored["zh-proper-hours"]["refusal_correct"] is None
    assert scored["model-xj900"]["passed"] is True
    assert scored["unans-orbit"]["refusal_correct"] is True
    assert scored["unans-orbit"]["passed"] is True
    assert scored["unans-orbit"]["citation_precision"] is None
    assert result.json_path == tmp_path / "answer-eval-20261026T032700Z.json"
    assert result.markdown_path == tmp_path / "answer-eval-20261026T032700Z.md"
    written = json.loads(result.json_path.read_text(encoding="utf-8"))
    assert written == json.loads(json.dumps(report))
    markdown = result.markdown_path.read_text(encoding="utf-8")
    assert "function:smoke" in markdown
    assert "2026-10-26" in markdown
    assert "zh-proper-hours" in markdown
    assert "unans-orbit" in markdown
    assert "model-xj900" in markdown
    assert "Pass 判准" in markdown


def test_configured_model_block_rejects_missing_and_fake_models() -> None:
    missing_model, _spec, missing = configured_model_block(make_settings())
    assert missing_model is None
    assert missing.startswith("BLOCKED")
    assert "AI_MODEL" in missing
    fake_model, _spec, fake = configured_model_block(
        make_settings(agent_contract_use_test_model=True, ai_model="openai:gpt-4o-mini")
    )
    assert fake_model is None
    assert fake.startswith("BLOCKED")
    assert "fake" in fake


def test_cli_exits_nonzero_when_model_is_not_configured(monkeypatch, capsys, tmp_path) -> None:
    monkeypatch.chdir(tmp_path)
    for name in (
        "AI_MODEL",
        "AI_PROVIDER_API_KEY",
        "AI_PROVIDER_BASE_URL",
        "AI_FALLBACK_MODELS",
        "GOOGLE_API_KEY",
        "GEMINI_API_KEY",
        "OPENAI_API_KEY",
    ):
        monkeypatch.setenv(name, "")
    # 空字符串过不了布尔校验；false 用来压过 .env 里的契约假模型。
    monkeypatch.setenv("AGENT_CONTRACT_USE_TEST_MODEL", "false")
    assert main([]) == 2
    captured = capsys.readouterr()
    assert "BLOCKED" in captured.out
    assert "AI_MODEL" in captured.out
