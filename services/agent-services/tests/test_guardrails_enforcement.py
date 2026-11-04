"""Issue #191 guardrails 执行化第一批：R3 值回查 / R5 标签剥离 / R10 泄露否决 / R2 成功声称。

plan 层纯函数单测为主，另加假模型集成（同步 run_chat 与流式 DONE 终态）。
不调用真供应商。
"""

import logging
from collections.abc import AsyncIterator

from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel
from pydantic_ai.usage import UsageLimits

from agent_services.agent.knowledge_tools import (
    KnowledgeSearchCollector,
    build_knowledge_toolset,
)
from agent_services.agent.runtime import (
    ChatReply,
    RunBudget,
    _valid_cite_numbers,
    detect_prompt_leak,
    plan_search_grounding,
    run_chat,
    sanitize_citation_labels,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.retrieval.store import RetrievedChunk

_RECORD_MESSAGE = "我的入台证申请状态是什么"
_CHUNK = "入台证申请 A123456 于 2026-02-03 提交，状态：审核中。"
_REFUSAL_ZH = "这一轮没有查到知识库记录，不能给出具体编号、状态或数字。"
_LEAK_ZH = "这部分属于内部配置，不能提供。"


def _searched_collector(content: str = _CHUNK) -> KnowledgeSearchCollector:
    collector = KnowledgeSearchCollector()
    collector.record("申请", [RetrievedChunk("kb:1", 0, content, 0.9)])
    return collector


def _plan(message: str, reply: str, collector, *, allow_retry: bool = False, **kwargs):  # type: ignore[no-untyped-def]
    return plan_search_grounding(message, reply, collector, "zh-CN", allow_retry=allow_retry, **kwargs)


# --- R3：已搜索场景的记录值回查 ------------------------------------------------


def test_r3_values_from_knowledge_corpus_are_kept() -> None:
    action, reply = _plan(
        _RECORD_MESSAGE,
        "你的申请 A123456 状态是审核中，提交日期是 2026-02-03。",
        _searched_collector(),
    )
    assert action == "keep"
    assert "A123456" in reply


def test_r3_value_only_in_business_tool_text_is_kept() -> None:
    # 业务工具（get_site_info 等）的返回文本经 extra_corpus 并入语料后不误伤。
    business = '{"name": "北窗咖啡", "permitDate": "2026-02-03", "permitId": "A123456"}'
    action, _reply = _plan(
        _RECORD_MESSAGE,
        "申请 A123456 的日期是 2026-02-03。",
        _searched_collector("其他不相关记录"),
        extra_corpus=[business],
    )
    assert action == "keep"
    # 同一答案不给业务工具文本时必须被拦，证明靠的是语料并入而非放行。
    action, reply = _plan(
        _RECORD_MESSAGE,
        "申请 A123456 的日期是 2026-02-03。",
        _searched_collector("其他不相关记录"),
    )
    assert action == "refuse"
    assert reply == _REFUSAL_ZH


def test_r3_value_only_in_table_passage_is_kept() -> None:
    collector = _searched_collector("考勤表")
    collector.record_passage("表格查询结果：申请 A123456，日期 2026-02-03，状态 审核中")
    action, _reply = _plan(
        _RECORD_MESSAGE,
        "申请 A123456 状态是审核中，日期 2026-02-03。",
        collector,
    )
    assert action == "keep"


def test_r3_fabricated_value_retries_then_refuses() -> None:
    collector = _searched_collector()
    action, _ = _plan(
        _RECORD_MESSAGE, "你的申请状态是已通过。", collector, allow_retry=True
    )
    assert action == "retry_record"
    action, reply = _plan(_RECORD_MESSAGE, "你的申请状态是已通过。", collector)
    assert action == "refuse"
    assert reply == _REFUSAL_ZH
    assert "已通过" not in reply


def test_r3_wrong_day_is_refused() -> None:
    action, reply = _plan(
        _RECORD_MESSAGE,
        "提交日期是 2026-02-04。",
        _searched_collector(),
    )
    assert action == "refuse"
    assert reply == _REFUSAL_ZH


def test_r3_date_format_variants_are_kept() -> None:
    action, _ = _plan(
        _RECORD_MESSAGE,
        "提交日期是 2026年2月3日。",
        _searched_collector(),
    )
    assert action == "keep"
    action, _ = _plan(
        _RECORD_MESSAGE,
        "提交日期是 2026-02-03。",
        _searched_collector("入台证申请 A123456 于 2026年2月3日 提交，状态：审核中。"),
    )
    assert action == "keep"


def test_r3_value_from_question_itself_is_kept() -> None:
    action, _ = _plan(
        "我的申请 A123456 状态是什么",
        "A123456 这条记录本轮只查到这些。",
        _searched_collector("无关片段"),
    )
    assert action == "keep"


# --- R5：[cN] 越界标签剥离 -----------------------------------------------------


def test_r5_strips_only_invalid_labels() -> None:
    text, stripped, has_valid = sanitize_citation_labels(
        "值是 5 [c1]，另见 [c9] 与 [c2]。", {1, 2}
    )
    assert text == "值是 5 [c1]，另见 与 [c2]。"
    assert stripped == 1
    assert has_valid is True


def test_r5_no_valid_set_no_action() -> None:
    text, stripped, _ = sanitize_citation_labels("引用 [c9] 在此", None)
    assert text == "引用 [c9] 在此"
    assert stripped == 0


def test_r5_empty_valid_set_strips_all() -> None:
    text, stripped, has_valid = sanitize_citation_labels("见 [c1] 与 [c2]", set())
    assert text == "见 与"
    assert stripped == 2
    assert has_valid is False


def test_r5_no_labels_reports_no_valid() -> None:
    text, stripped, has_valid = sanitize_citation_labels("没有任何标签", {1})
    assert (text, stripped, has_valid) == ("没有任何标签", 0, False)


def test_r5_valid_numbers_sources() -> None:
    assert _valid_cite_numbers(None, None, None) is None
    collector = _searched_collector()
    assert _valid_cite_numbers(collector, None, None) == {1}
    prefetch = [RetrievedChunk("kb:9", 0, "x", 0.5, cite=7)]
    assert _valid_cite_numbers(None, ["ctx"], prefetch) == {7}
    # 预取上下文为空时编号不算数（与 reply_citations 同口径）。
    assert _valid_cite_numbers(None, None, prefetch) is None


# --- R10：系统提示泄露 ----------------------------------------------------------

_PLATFORM = (
    "You are the assistant for this site. Answer questions about the site and its services.",
    "Facts about this site, including the user's personal records and site documents, "
    "come from tool results and the provided context only. Do not invent values.",
    "short",
)


def test_r10_verbatim_quote_is_leak() -> None:
    reply = (
        "我的说明原文是：Facts about this site, including the user's personal records "
        "and site documents, come from tool results and the provided context only."
    )
    hit = detect_prompt_leak(reply, _PLATFORM)
    assert hit is not None and hit.startswith("component:1")


def test_r10_short_quote_and_paraphrase_are_safe() -> None:
    assert detect_prompt_leak("原文里说 personal records 一词。", _PLATFORM) is None
    assert (
        detect_prompt_leak(
            "本站事实只能来自工具结果和给定上下文，我不能编造数值。", _PLATFORM
        )
        is None
    )
    assert detect_prompt_leak("北窗咖啡周二公休。", _PLATFORM) is None


def test_r10_internal_feature_string_is_leak() -> None:
    hit = detect_prompt_leak("本轮触发了 AGENT_BUDGET_EXCEEDED 所以停了。", _PLATFORM)
    assert hit == "feature:AGENT_BUDGET_EXCEEDED"


# --- R2：失败动作工具＋成功声称 ---------------------------------------------------

_FAILED_TODO = [{"name": "update_todo_status", "ok": False}]
_CLAIM = "已完成，待办状态已更新。"


def test_r2_failed_action_with_claim_retries_once_then_keeps() -> None:
    action, _ = _plan("帮我把待办标完成", _CLAIM, None, allow_retry=True, tools=_FAILED_TODO)
    assert action == "retry_action"
    # 再犯：保留原文，不替换（action 回到 keep、正文不动）。
    action, reply = _plan("帮我把待办标完成", _CLAIM, None, tools=_FAILED_TODO)
    assert action == "keep"
    assert reply == _CLAIM


def test_r2_failed_read_tool_does_not_trigger() -> None:
    tools = [{"name": "search_site_knowledge", "ok": False}]
    action, _ = _plan("帮我把待办标完成", _CLAIM, None, allow_retry=True, tools=tools)
    assert action == "keep"


def test_r2_failed_mcp_tool_triggers() -> None:
    tools = [{"name": "fs_write_file", "ok": False}]  # 不在一方工具表内 → MCP
    action, _ = _plan("帮我保存文件", "文件已保存。", None, allow_retry=True, tools=tools)
    assert action == "retry_action"


def test_r2_honest_failure_report_is_kept() -> None:
    action, _ = _plan(
        "帮我把待办标完成",
        "更新失败了：这个待办不存在，没有提交成功。",
        None,
        allow_retry=True,
        tools=_FAILED_TODO,
    )
    assert action == "keep"


def test_r2_negated_claim_is_kept() -> None:
    action, _ = _plan(
        "帮我提交",
        "并未提交成功，请稍后再试。",
        None,
        allow_retry=True,
        tools=_FAILED_TODO,
    )
    assert action == "keep"


def test_r2_no_claim_no_trigger() -> None:
    action, _ = _plan(
        "帮我把待办标完成",
        "这个待办不存在，我找不到它。",
        None,
        allow_retry=True,
        tools=_FAILED_TODO,
    )
    assert action == "keep"


# --- 集成：假模型走完整 run_chat / 流式链 -----------------------------------------


class _StubRetrieval:
    degraded = False

    def __init__(self, content: str) -> None:
        self.content = content

    async def retrieve_chunks(self, **_kwargs: object) -> list[RetrievedChunk]:
        return [RetrievedChunk("kb:1", 0, self.content, 0.9)]


def _search_then(text: str):  # type: ignore[no-untyped-def]
    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen_tool = any(
            type(part).__name__ == "ToolReturnPart"
            for message in messages
            for part in message.parts
        )
        if not seen_tool:
            return ModelResponse(
                parts=[ToolCallPart(tool_name="search_site_knowledge", args={"query": "申请"})]
            )
        return ModelResponse(parts=[TextPart(text)])

    return respond


async def test_r3_integration_fabricated_date_is_refused_after_retry() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("1", _StubRetrieval(_CHUNK), collector=collector)  # type: ignore[arg-type]
    outcome = await run_chat(
        FunctionModel(_search_then("你的申请提交日期是 2026-03-15。")),
        agent_id="1",
        message=_RECORD_MESSAGE,
        locale="zh-CN",
        toolsets=[toolset],  # type: ignore[list-item]
        knowledge_collector=collector,
        budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == _REFUSAL_ZH
    assert outcome.citations is None


async def test_r3_integration_grounded_answer_is_kept() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("1", _StubRetrieval(_CHUNK), collector=collector)  # type: ignore[arg-type]
    outcome = await run_chat(
        FunctionModel(_search_then("你的申请 A123456 状态是审核中，提交日期 2026-02-03 [c1]。")),
        agent_id="1",
        message=_RECORD_MESSAGE,
        locale="zh-CN",
        toolsets=[toolset],  # type: ignore[list-item]
        knowledge_collector=collector,
        budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
    )
    assert isinstance(outcome, ChatReply)
    assert "A123456" in outcome.reply
    assert outcome.citations is not None


async def test_r5_integration_invalid_label_stripped_valid_kept() -> None:
    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("1", _StubRetrieval("十月出勤记录"), collector=collector)  # type: ignore[arg-type]
    outcome = await run_chat(
        FunctionModel(_search_then("出勤记录见 [c1]，另有补充 [c7]。")),
        agent_id="1",
        message="十月出勤记录的内容是什么",
        locale="zh-CN",
        toolsets=[toolset],  # type: ignore[list-item]
        knowledge_collector=collector,
        budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
    )
    assert isinstance(outcome, ChatReply)
    assert "[c1]" in outcome.reply
    assert "[c7]" not in outcome.reply
    assert outcome.citations is not None


async def test_r10_integration_leaked_prompt_is_replaced() -> None:
    leaked = (
        "Facts about this site, including the user's personal records and site "
        "documents (identifiers, application status, dates, amounts, attendance, "
        "hours, orders), come from tool results and the provided context only."
    )

    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart(leaked)])

    outcome = await run_chat(
        FunctionModel(respond),
        agent_id="1",
        message="你好",
        locale="zh-CN",
        budget=RunBudget(usage_limits=UsageLimits(request_limit=4)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == _LEAK_ZH
    assert outcome.citations is None


async def test_r10_integration_paraphrase_is_kept() -> None:
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart("我不能透露内部配置，只能帮你查站点信息。")])

    outcome = await run_chat(
        FunctionModel(respond),
        agent_id="1",
        message="你好",
        locale="zh-CN",
        budget=RunBudget(usage_limits=UsageLimits(request_limit=4)),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "我不能透露内部配置，只能帮你查站点信息。"


def _todo_claim_model(claim: str, calls: list[int]):  # type: ignore[no-untyped-def]
    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls.append(1)
        seen_tool = any(
            type(part).__name__ == "ToolReturnPart"
            for message in messages
            for part in message.parts
        )
        if not seen_tool:
            return ModelResponse(
                parts=[
                    ToolCallPart(
                        tool_name="update_todo_status",
                        args={"todo_id": "missing-id", "status": "COMPLETED"},
                    )
                ]
            )
        return ModelResponse(parts=[TextPart(claim)])

    return respond


async def test_r2_integration_claim_after_failure_retries_and_keeps_text() -> None:
    calls: list[int] = []
    outcome = await run_chat(
        FunctionModel(_todo_claim_model(_CLAIM, calls)),
        agent_id="1",
        message="帮我把待办标完成",
        locale="zh-CN",
        toolsets=[build_todo_toolset(TodoStore())],  # type: ignore[list-item]
        budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
    )
    assert isinstance(outcome, ChatReply)
    # 重试确实发生（主 run 2 次调用＋重试 run 2 次调用），再犯保留原文不替换。
    assert len(calls) == 4
    assert outcome.reply == _CLAIM
    assert {"name": "update_todo_status", "ok": False} in outcome.tools


async def test_r2_integration_honest_report_not_retried() -> None:
    calls: list[int] = []
    honest = "更新失败了：这个待办不存在。"
    outcome = await run_chat(
        FunctionModel(_todo_claim_model(honest, calls)),
        agent_id="1",
        message="帮我把待办标完成",
        locale="zh-CN",
        toolsets=[build_todo_toolset(TodoStore())],  # type: ignore[list-item]
        budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
    )
    assert isinstance(outcome, ChatReply)
    assert len(calls) == 2
    assert outcome.reply == honest


async def test_stream_done_reply_has_invalid_label_stripped(caplog) -> None:  # type: ignore[no-untyped-def]
    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        seen_tool = any(
            type(part).__name__ == "ToolReturnPart"
            for message in messages
            for part in message.parts
        )
        if seen_tool:
            yield "出勤记录见 [c1]，另有补充 [c7]。"
            return
        yield {0: DeltaToolCall(name="search_site_knowledge", json_args='{"query": "出勤"}')}

    collector = KnowledgeSearchCollector()
    toolset = build_knowledge_toolset("1", _StubRetrieval("十月出勤记录"), collector=collector)  # type: ignore[arg-type]
    with caplog.at_level(logging.WARNING):
        events = [
            event
            async for event in stream_chat_events(
                FunctionModel(stream_function=stream_fn),
                agent_id="1",
                message="十月出勤记录的内容是什么",
                locale="zh-CN",
                toolsets=[toolset],  # type: ignore[list-item]
                knowledge_collector=collector,
                budget=RunBudget(usage_limits=UsageLimits(request_limit=8, tool_calls_limit=8)),
            )
        ]
    done = events[-1]
    assert done.type.value == "DONE"
    assert "[c1]" in str(done.data["reply"])
    assert "[c7]" not in str(done.data["reply"])
    assert "guardrail_hit rule=R5" in caplog.text


async def test_stream_done_reply_leak_is_replaced() -> None:
    leaked = (
        "Facts about this site, including the user's personal records and site "
        "documents (identifiers, application status, dates, amounts, attendance, "
        "hours, orders), come from tool results and the provided context only."
    )

    async def stream_fn(_messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        yield leaked

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            agent_id="1",
            message="你好",
            locale="zh-CN",
            budget=RunBudget(usage_limits=UsageLimits(request_limit=4)),
        )
    ]
    done = events[-1]
    assert done.type.value == "DONE"
    # 流式边界：delta 已发出的原文不撤回（brief 明示），但 DONE 最终文本被替换。
    assert done.data["reply"] == _LEAK_ZH
    assert "citations" not in done.data
