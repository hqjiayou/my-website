"""Phase 5 todo 编排工具测试。"""

from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.schemas.todo import TodoItem


async def test_todo_tools_track_multi_step_plan() -> None:
    store = TodoStore()
    toolset = build_todo_toolset(store)

    def model_fn(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        has_tool_result = any("Step 1" in repr(message) for message in messages)
        if not has_tool_result:
            return ModelResponse(parts=[ToolCallPart(tool_name="add_todo", args={"title": "Step 1"})])
        return ModelResponse(parts=[TextPart("planned")])

    outcome = await run_chat(
        FunctionModel(model_fn),
        agent_id="1",
        message="plan this",
        toolsets=[toolset],
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "planned"
    assert len(store.items) == 1
    assert store.items[0].title == "Step 1"


async def test_todo_store_hydrates_from_request() -> None:
    store = TodoStore(
        items=[TodoItem(id="t1", title="Existing", status="IN_PROGRESS")]
    )
    toolset = build_todo_toolset(store)

    def model_fn(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        # 工具说明里的 “COMPLETED” 不能当成工具已经返回。
        has_tool_result = any(
            "'id': 't1'" in repr(message) and "'status': 'COMPLETED'" in repr(message)
            for message in messages
        )
        if not has_tool_result:
            return ModelResponse(parts=[ToolCallPart(tool_name="complete_todo", args={"todo_id": "t1"})])
        return ModelResponse(parts=[TextPart("done")])

    outcome = await run_chat(
        FunctionModel(model_fn),
        agent_id="1",
        message="finish",
        toolsets=[toolset],
        initial_todos=store.items,
    )
    assert isinstance(outcome, ChatReply)
    assert store.items[0].status == "COMPLETED"
    assert outcome.reply == "done"


async def test_terminal_todo_cannot_be_reopened_and_emits_failed_tool_result() -> None:
    from pydantic_ai.models.function import DeltaToolCall

    from agent_services.agent.stream_runtime import stream_chat_events
    from agent_services.schemas.stream import AgentStreamEventType

    store = TodoStore(items=[TodoItem(id="t1", title="Done already", status="COMPLETED")])
    toolset = build_todo_toolset(store)

    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo):
        if not any("cannot change" in repr(m) for m in messages):
            yield {
                0: DeltaToolCall(
                    name="update_todo_status", json_args='{"todo_id":"t1","status":"PENDING"}'
                )
            }
            return
        yield "ok"

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            agent_id="1",
            message="reopen",
            toolsets=[toolset],
            todo_store=store,
        )
    ]
    result = next(e for e in events if e.type == AgentStreamEventType.TOOL_RESULT)
    assert result.data["ok"] is False
    assert store.items[0].status == "COMPLETED"
    done = next(e for e in events if e.type == AgentStreamEventType.DONE)
    assert done.data["todos"] == [{"id": "t1", "title": "Done already", "status": "COMPLETED"}]


async def test_add_todo_respects_item_cap() -> None:
    from agent_services.schemas.todo import MAX_TODO_ITEMS

    store = TodoStore(
        items=[TodoItem(id=f"t{i}", title=f"Step {i}", status="PENDING") for i in range(MAX_TODO_ITEMS)]
    )
    toolset = build_todo_toolset(store)
    calls = {"n": 0}

    def model_fn(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls["n"] += 1
        if calls["n"] == 1:
            return ModelResponse(parts=[ToolCallPart(tool_name="add_todo", args={"title": "overflow"})])
        return ModelResponse(parts=[TextPart("stopped")])

    outcome = await run_chat(FunctionModel(model_fn), agent_id="1", message="add", toolsets=[toolset])
    assert isinstance(outcome, ChatReply)
    assert len(store.items) == MAX_TODO_ITEMS
