"""#204：eval runner 保真与口径修复的确定性验收（假模型层）。

覆盖三处：UoW-1 假 Java client（语料驱动的枚举语义）与工具面接通
（list_knowledge_documents + todo 在 eval run 中可见可用）；
UoW-2 provider BLOCKED（EXTERNAL_DEPENDENCY_BLOCKED）不计 fail
（不进分母、diff 不算翻转、全 blocked 退出 2、其他 error_code 照常计分）；
UoW-3 --pace-seconds 题间节奏（mock sleep 计数、缺省不调用）。
真模型读数不在 CI 内跑，仍属 #187 UoW-3 本机执行。
"""

from __future__ import annotations

import asyncio

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

from agent_services.agent.runtime import ChatBlocked
from agent_services.evals import run_answer_eval as runner
from agent_services.evals.run_answer_eval import (
    CorpusKnowledgeClient,
    build_baseline_diff,
    load_cases,
    load_corpus,
    load_distractors,
    render_markdown,
    run_answer_eval,
)


def _row(report: dict[str, object], case_id: str) -> dict[str, object]:
    rows = report["cases"]
    assert isinstance(rows, list)
    for row in rows:
        if isinstance(row, dict) and row.get("id") == case_id:
            return row
    raise AssertionError(f"row {case_id} missing")


def _case(case_id: str) -> runner.GoldenCase:
    return {item.id: item for item in load_cases()}[case_id]


# ---------------------------------------------------------------------------
# UoW-1：假 Java client 的枚举语义（纯函数层）
# ---------------------------------------------------------------------------


async def test_fake_client_lists_full_corpus_by_default() -> None:
    corpus = load_corpus()
    client = CorpusKnowledgeClient(corpus)
    page = await client.list_knowledge_documents("eval-golden")
    assert page["total"] == len(corpus) == 16
    assert page["limit"] == 100
    assert page["offset"] == 0
    assert page["truncated"] is False
    items = page["items"]
    assert isinstance(items, list) and len(items) == 16
    first = items[0]
    assert isinstance(first, dict)
    assert first["id"] == 0
    assert first["sourceId"] == corpus[0].source_id
    assert first["title"] == corpus[0].title
    assert first["sourceFormat"] is None
    assert first["originalFilename"] is None
    assert first["indexStatus"] == "INDEXED"
    assert first["createdAt"] is None
    assert first["updatedAt"] is None


async def test_fake_client_keyword_filters_by_title_substring() -> None:
    client = CorpusKnowledgeClient(load_corpus())
    page = await client.list_knowledge_documents("eval-golden", keyword="工资单")
    assert page["total"] == 4
    items = page["items"]
    assert isinstance(items, list)
    assert [item["sourceId"] for item in items] == [
        "payslip-2026-01",
        "payslip-2026-02",
        "payslip-2026-03",
        "payslip-2026-04",
    ]
    # 标题里没有的子串即空集，不做模糊匹配。
    empty = await client.list_knowledge_documents("eval-golden", keyword="租赁合同")
    assert empty["total"] == 0
    assert empty["items"] == []


async def test_fake_client_pagination_and_truncated_boundaries() -> None:
    client = CorpusKnowledgeClient(load_corpus())
    first = await client.list_knowledge_documents("eval-golden", limit=5, offset=0)
    assert first["total"] == 16 and first["truncated"] is True
    assert isinstance(first["items"], list) and len(first["items"]) == 5

    # offset + len(items) == total 时恰好读完，不是 truncated。
    last = await client.list_knowledge_documents("eval-golden", limit=5, offset=11)
    assert last["truncated"] is False
    assert isinstance(last["items"], list) and len(last["items"]) == 5

    tail = await client.list_knowledge_documents("eval-golden", limit=5, offset=13)
    assert tail["truncated"] is False
    assert isinstance(tail["items"], list) and len(tail["items"]) == 3

    beyond = await client.list_knowledge_documents("eval-golden", limit=5, offset=99)
    assert beyond["total"] == 16
    assert beyond["items"] == []
    assert beyond["truncated"] is False


async def test_fake_client_clamps_limit_and_offset_like_java() -> None:
    client = CorpusKnowledgeClient(load_corpus())
    low = await client.list_knowledge_documents("eval-golden", limit=0, offset=-3)
    assert low["limit"] == 1
    assert low["offset"] == 0
    assert isinstance(low["items"], list) and len(low["items"]) == 1

    high = await client.list_knowledge_documents("eval-golden", limit=500)
    assert high["limit"] == 200


async def test_fake_client_format_and_time_filters_return_empty() -> None:
    client = CorpusKnowledgeClient(load_corpus())
    # eval 语料无格式/时间元数据：带此类过滤无可匹配文档，返回空集不伪造。
    by_format = await client.list_knowledge_documents(
        "eval-golden", source_format="pdf"
    )
    assert by_format["total"] == 0 and by_format["items"] == []
    by_time = await client.list_knowledge_documents(
        "eval-golden", created_from="2026-01-01T00:00:00+00:00"
    )
    assert by_time["total"] == 0 and by_time["items"] == []
    by_time_to = await client.list_knowledge_documents(
        "eval-golden", created_to="2026-12-31T00:00:00+00:00"
    )
    assert by_time_to["total"] == 0 and by_time_to["items"] == []


async def test_fake_client_enumerates_distractors_when_merged() -> None:
    merged = load_corpus() + load_distractors()
    client = CorpusKnowledgeClient(merged)
    page = await client.list_knowledge_documents("eval-golden", limit=200)
    assert page["total"] == len(merged) == 24

    sewing = await client.list_knowledge_documents("eval-golden", keyword="缝纫机")
    assert sewing["total"] == 2
    items = sewing["items"]
    assert isinstance(items, list)
    assert [item["sourceId"] for item in items] == ["kb:xj800", "kb:care-cleaning"]


# ---------------------------------------------------------------------------
# UoW-1：工具面接通（管线层，假模型实际调用枚举与 todo）
# ---------------------------------------------------------------------------


class _ToolSurfaceRecorder:
    """依次调用 list_knowledge_documents 与 add_todo 后作答，并记录所见。"""

    def __init__(self) -> None:
        self.system_prompts: list[str] = []
        self.enum_returns: list[object] = []

    def respond(self, messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen: set[str] = set()
        for message in messages:
            for part in getattr(message, "parts", []):
                if isinstance(part, SystemPromptPart):
                    self.system_prompts.append(part.content)
                elif isinstance(part, ToolReturnPart):
                    seen.add(part.tool_name)
                    if part.tool_name == "list_knowledge_documents":
                        self.enum_returns.append(part.content)
        if "list_knowledge_documents" not in seen:
            return ModelResponse(
                parts=[
                    ToolCallPart(
                        "list_knowledge_documents", {"keyword": "工资单"}
                    )
                ]
            )
        if "add_todo" not in seen:
            return ModelResponse(
                parts=[ToolCallPart("add_todo", {"title": "逐份抽取工资单实发额"})]
            )
        return ModelResponse(parts=[TextPart("已枚举全部工资单并建好 todo。")])


async def test_eval_run_exposes_list_and_todo_tools() -> None:
    recorder = _ToolSurfaceRecorder()
    result = await run_answer_eval(
        FunctionModel(recorder.respond),
        model_name="function:fidelity",
        cases=[_case("coll-payslip-lowest-net")],
    )
    # 系统提示的工具清单里两件都在（harness 按本轮实际注册工具组装）。
    joined = "\n".join(recorder.system_prompts)
    assert "list_knowledge_documents" in joined
    assert "add_todo" in joined

    # 枚举真的走通了：按 keyword 拿到全部 4 份工资单（集合级全集）。
    # 注：respond 每轮重扫全部消息，同一 ToolReturn 会被重复记录，只验首个。
    assert len(recorder.enum_returns) >= 1
    payload = recorder.enum_returns[0]
    assert isinstance(payload, dict)
    assert payload["total"] == 4
    documents = payload["documents"]
    assert isinstance(documents, list) and len(documents) == 4
    assert documents[0]["sourceId"] == "payslip-2026-01"

    row = _row(result.report, "coll-payslip-lowest-net")
    assert row["reply"] == "已枚举全部工资单并建好 todo。"


# ---------------------------------------------------------------------------
# UoW-2：provider BLOCKED 不计 fail
# ---------------------------------------------------------------------------


def _raise_provider_down(
    _messages: list[ModelMessage], _info: AgentInfo
) -> ModelResponse:
    raise RuntimeError("provider down")


class _SearchRecorder:
    """先检索、再据工具返回作答（与 wiring 测试同路径）；指定题 raise。"""

    def __init__(self, fail_marker: str | None = None) -> None:
        self.fail_marker = fail_marker

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
                        return ModelResponse(
                            parts=[TextPart("无法确认。没有相关信息。")]
                        )
                    return ModelResponse(parts=[TextPart(content)])
                elif isinstance(part, UserPromptPart) and isinstance(part.content, str):
                    question = part.content
        if self.fail_marker and self.fail_marker in question:
            raise RuntimeError("provider down")
        return ModelResponse(
            parts=[ToolCallPart("search_site_knowledge", {"query": question})]
        )


async def test_external_dependency_blocked_row_is_not_scored() -> None:
    result = await run_answer_eval(
        FunctionModel(_raise_provider_down),
        model_name="function:fidelity",
        cases=[_case("zh-proper-hours")],
    )
    report = result.report
    row = _row(report, "zh-proper-hours")
    assert row["blocked"] is True
    assert row["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"
    assert row["blockedReason"]
    scores = row["scores"]
    assert isinstance(scores, dict)
    assert all(scores[name] is None for name in scores)

    summary = report["summary"]
    assert isinstance(summary, dict)
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 0
    assert overall["passed"] is None
    assert summary["blockedCount"] == 1
    blocked_cases = summary["blockedCases"]
    assert isinstance(blocked_cases, list) and len(blocked_cases) == 1
    assert blocked_cases[0]["id"] == "zh-proper-hours"
    assert "unscoredCount" not in summary

    markdown = render_markdown(report)
    assert "## Blocked" in markdown
    assert "zh-proper-hours" in markdown
    assert runner._run_exit_code(report) == 2


async def test_partial_blocked_keeps_scored_denominator_and_exit_zero() -> None:
    recorder = _SearchRecorder(fail_marker="XJ-900")
    result = await run_answer_eval(
        FunctionModel(recorder.respond),
        model_name="function:fidelity",
        cases=[_case("zh-proper-hours"), _case("model-xj900")],
    )
    report = result.report
    assert _row(report, "zh-proper-hours")["scores"]["passed"] is True  # type: ignore[index]
    blocked = _row(report, "model-xj900")
    assert blocked["blocked"] is True

    summary = report["summary"]
    assert isinstance(summary, dict)
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 1
    assert overall["passed"] == 1.0
    assert summary["blockedCount"] == 1
    assert runner._run_exit_code(report) == 0


async def test_all_blocked_run_exits_2() -> None:
    result = await run_answer_eval(
        FunctionModel(_raise_provider_down),
        model_name="function:fidelity",
        cases=[_case("zh-proper-hours"), _case("zh-menu-name")],
    )
    summary = result.report["summary"]
    assert isinstance(summary, dict)
    assert summary["blockedCount"] == 2
    assert runner._run_exit_code(result.report) == 2


async def test_other_error_code_blocked_is_still_scored(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """预算超限等其他 error_code 的 ChatBlocked 维持现行计分口径。"""

    async def budget_blocked(model, **kwargs):  # type: ignore[no-untyped-def]
        del model, kwargs
        return ChatBlocked(
            message="budget exceeded", error_code="AGENT_BUDGET_EXCEEDED"
        )

    monkeypatch.setattr(runner, "run_chat", budget_blocked)
    result = await run_answer_eval(
        FunctionModel(_SearchRecorder().respond),
        model_name="function:fidelity",
        cases=[_case("zh-proper-hours")],
    )
    report = result.report
    row = _row(report, "zh-proper-hours")
    assert "blocked" not in row
    assert row["errorCode"] == "AGENT_BUDGET_EXCEEDED"
    scores = row["scores"]
    assert isinstance(scores, dict)
    assert scores["keyword_coverage"] == 0.0
    assert scores["passed"] is False
    summary = report["summary"]
    assert isinstance(summary, dict)
    assert "blockedCount" not in summary
    overall = summary["overall"]
    assert isinstance(overall, dict)
    assert overall["count"] == 1


def _scored_row(case_id: str, *, passed: bool) -> dict[str, object]:
    return {
        "id": case_id,
        "category": "hours",
        "scores": {
            "retrieval_hit": True,
            "citation_precision": 1.0,
            "keyword_coverage": 1.0,
            "refusal_correct": None,
            "passed": passed,
        },
    }


def _blocked_row(case_id: str) -> dict[str, object]:
    return {
        "id": case_id,
        "category": "hours",
        "blocked": True,
        "blockedReason": "AI provider call failed",
        "errorCode": "EXTERNAL_DEPENDENCY_BLOCKED",
        "scores": {
            "retrieval_hit": None,
            "citation_precision": None,
            "keyword_coverage": None,
            "refusal_correct": None,
            "passed": None,
        },
    }


def _mini_report(rows: list[dict[str, object]]) -> dict[str, object]:
    return {
        "model": "function:fidelity",
        "generatedAt": "2026-11-05T00:00:00+00:00",
        "cases": rows,
    }


def test_baseline_diff_treats_blocked_like_unscored() -> None:
    baseline = _mini_report([_scored_row("a", passed=True), _blocked_row("b")])
    current = _mini_report([_blocked_row("a"), _scored_row("b", passed=True)])
    diff = build_baseline_diff(current, baseline)
    # 两边任一侧 blocked 都不算翻转：a 不是 pass→fail，b 不是 fail→pass。
    flips = diff["flips"]
    assert isinstance(flips, dict)
    assert flips["passToFail"] == []
    assert flips["failToPass"] == []
    assert diff["blockedCases"] == ["a"]
    assert diff["baselineBlockedCases"] == ["b"]
    by_id = {entry["id"]: entry for entry in diff["cases"]}  # type: ignore[index]
    assert by_id["a"]["status"] == "blocked"
    assert by_id["b"]["status"] == "baseline_blocked"


def test_exit_code_blocked_and_unscored_mix_is_2() -> None:
    unscored = {
        "id": "u",
        "category": "hours",
        "unscored": True,
        "scores": {"passed": None},
    }
    report = _mini_report([_blocked_row("a"), unscored])  # type: ignore[list-item]
    assert runner._run_exit_code(report) == 2
    # 有一题已计分时，blocked 不改变退出码语义。
    mixed = _mini_report([_blocked_row("a"), _scored_row("b", passed=True)])
    assert runner._run_exit_code(mixed) == 0


# ---------------------------------------------------------------------------
# UoW-3：题间节奏 --pace-seconds
# ---------------------------------------------------------------------------


async def test_pace_sleeps_between_cases_only(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        calls.append(seconds)

    monkeypatch.setattr(asyncio, "sleep", fake_sleep)
    cases = [_case("zh-proper-hours"), _case("zh-menu-name"), _case("weekend-open")]
    result = await run_answer_eval(
        FunctionModel(_SearchRecorder().respond),
        model_name="function:fidelity",
        cases=cases,
        pace_seconds=0.5,
    )
    # N 题睡 N-1 次（首题前不睡），每次时长为给定 pace。
    assert calls == [0.5, 0.5]
    assert result.report["paceSeconds"] == 0.5


async def test_pace_zero_never_sleeps_and_stays_unannotated(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        calls.append(seconds)

    monkeypatch.setattr(asyncio, "sleep", fake_sleep)
    result = await run_answer_eval(
        FunctionModel(_SearchRecorder().respond),
        model_name="function:fidelity",
        cases=[_case("zh-proper-hours"), _case("zh-menu-name")],
    )
    assert calls == []
    assert "paceSeconds" not in result.report
    summary = result.report["summary"]
    assert isinstance(summary, dict)
    assert "blockedCount" not in summary


def test_cli_pace_seconds_argument_parsing() -> None:
    parser = runner._parser()
    assert parser.parse_args([]).pace_seconds == 0.0
    assert parser.parse_args(["--pace-seconds", "1.5"]).pace_seconds == 1.5
