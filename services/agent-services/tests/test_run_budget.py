"""§22.4 T1：运行预算 / 工具超时 / 用量回传。"""

import asyncio

from fastapi.testclient import TestClient
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel
from pydantic_ai.models.test import TestModel
from pydantic_ai.toolsets.function import FunctionToolset
from pydantic_ai.usage import UsageLimits

from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.runtime import (
    BUDGET_EXCEEDED,
    ChatBlocked,
    ChatReply,
    RunBudget,
    run_chat,
    trim_history,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.app import create_app
from agent_services.schemas.chat import ChatHistoryMessage
from agent_services.schemas.stream import AgentStreamEventType
from conftest import TOKEN_HEADER, make_settings


def _h(role: str, content: str) -> ChatHistoryMessage:
    return ChatHistoryMessage(role=role, content=content)


def test_trim_history_keeps_newest_within_budget_and_starts_with_user() -> None:
    history = [_h("USER", "a" * 10), _h("ASSISTANT", "b" * 10), _h("USER", "c" * 10), _h("ASSISTANT", "d" * 10)]
    # 预算 25 → 只容得下最近 2 条（c, d），恰好 USER 开头
    assert [m.content[0] for m in trim_history(history, 25)] == ["c", "d"]
    # 预算 35 → 容得下 b, c, d，但 b 为 ASSISTANT 开头 → 被丢弃
    assert [m.content[0] for m in trim_history(history, 35)] == ["c", "d"]
    assert trim_history(history, None) == history
    assert trim_history(history, 5) == []
    assert trim_history(None, 100) == []


def _ping_toolset() -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain
    def ping() -> str:
        """Return pong."""
        return "pong"

    return toolset


def _looping_model() -> FunctionModel:
    """永远继续调工具：模拟失控循环。"""

    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[ToolCallPart(tool_name="ping", args={})])

    return FunctionModel(respond)


async def test_request_limit_exceeded_is_budget_error_not_dependency_error() -> None:
    outcome = await run_chat(
        _looping_model(),
        site_id="1",
        message="loop",
        toolsets=[_ping_toolset()],
        budget=RunBudget(usage_limits=UsageLimits(request_limit=3)),
    )
    assert isinstance(outcome, ChatBlocked)
    assert outcome.error_code == BUDGET_EXCEEDED


async def test_stream_budget_exceeded_emits_error_without_done() -> None:
    async def stream_fn(_messages: list[ModelMessage], _info: AgentInfo):
        yield {0: DeltaToolCall(name="ping", json_args="{}")}

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            site_id="1",
            message="loop",
            toolsets=[_ping_toolset()],
            budget=RunBudget(usage_limits=UsageLimits(request_limit=2)),
        )
    ]
    types = [e.type for e in events]
    assert AgentStreamEventType.DONE not in types
    error = next(e for e in events if e.type == AgentStreamEventType.ERROR)
    assert error.data["errorCode"] == BUDGET_EXCEEDED


async def test_reply_carries_usage() -> None:
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart("hi")])

    outcome = await run_chat(FunctionModel(respond), site_id="1", message="hello")
    assert isinstance(outcome, ChatReply)
    assert outcome.usage["requests"] == 1
    assert set(outcome.usage) == {"requests", "toolCalls", "inputTokens", "outputTokens"}


def _ping_then_answer(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
    if len(messages) == 1:
        return ModelResponse(parts=[ToolCallPart(tool_name="ping", args={})])
    return ModelResponse(parts=[TextPart("done")])


async def test_sync_reply_carries_tool_trace_without_content() -> None:
    outcome = await run_chat(
        FunctionModel(_ping_then_answer), site_id="1", message="go", toolsets=[_ping_toolset()]
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.tools == [{"name": "ping", "ok": True}]


async def test_stream_done_carries_tool_trace() -> None:
    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo):
        if len(messages) == 1:
            yield {0: DeltaToolCall(name="ping", json_args="{}")}
            return
        yield "done"

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            site_id="1",
            message="go",
            toolsets=[_ping_toolset()],
        )
    ]
    done = next(e for e in events if e.type == AgentStreamEventType.DONE)
    assert done.data["tools"] == [{"name": "ping", "ok": True}]
    assert done.data["usage"]["requests"] == 2


async def test_slow_tool_times_out_and_reports_failed_tool_result() -> None:
    toolset = FunctionToolset()

    @toolset.tool_plain
    async def slow() -> str:
        """Sleep longer than the timeout."""
        await asyncio.sleep(2)
        return "late"

    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo):
        if not any("timed out" in repr(m).lower() or "timeout" in repr(m).lower() for m in messages):
            yield {0: DeltaToolCall(name="slow", json_args="{}")}
            return
        yield "gave up"

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            site_id="1",
            message="slow",
            toolsets=[toolset],
            budget=RunBudget(usage_limits=UsageLimits(request_limit=5), tool_timeout_seconds=0.1),
        )
    ]
    result = next(e for e in events if e.type == AgentStreamEventType.TOOL_RESULT)
    assert result.data["ok"] is False


def test_chat_endpoint_maps_budget_to_422_flat_body() -> None:
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[ToolCallPart(tool_name="list_todos", args={})])

    looping = ModelHandle(model=FunctionModel(respond), spec="test:loop", blocked_reason=None)
    app = create_app(make_settings(agent_max_requests=2), model_handle=looping)
    response = TestClient(app).post(
        "/internal/v1/chat", headers=TOKEN_HEADER, json={"siteId": "1", "message": "loop"}
    )
    assert response.status_code == 422
    body = response.json()
    assert body == {"ok": False, "errorCode": BUDGET_EXCEEDED, "message": body["message"]}


def test_chat_endpoint_returns_usage() -> None:
    ok_handle = ModelHandle(model=TestModel(custom_output_text="ok"), spec="test", blocked_reason=None)
    ok_app = create_app(make_settings(agent_enable_todo_tools=False), model_handle=ok_handle)
    data = TestClient(ok_app).post(
        "/internal/v1/chat", headers=TOKEN_HEADER, json={"siteId": "1", "message": "hi"}
    ).json()["data"]
    assert data["usage"]["requests"] >= 1
