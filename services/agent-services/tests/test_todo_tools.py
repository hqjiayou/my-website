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
        site_id="1",
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
        has_tool_result = any("COMPLETED" in repr(message) for message in messages)
        if not has_tool_result:
            return ModelResponse(parts=[ToolCallPart(tool_name="complete_todo", args={"todo_id": "t1"})])
        return ModelResponse(parts=[TextPart("done")])

    outcome = await run_chat(
        FunctionModel(model_fn),
        site_id="1",
        message="finish",
        toolsets=[toolset],
        initial_todos=store.items,
    )
    assert isinstance(outcome, ChatReply)
    assert store.items[0].status == "COMPLETED"
    assert outcome.reply == "done"
