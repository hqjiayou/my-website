"""流式 chat 执行：Pydantic AI stream → 应用层 AgentStreamEvent。"""

from collections.abc import AsyncIterator

from pydantic_ai import Agent
from pydantic_ai.messages import FunctionToolCallEvent, FunctionToolResultEvent
from pydantic_ai.models import Model
from pydantic_ai.settings import ModelSettings

from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.agent.runtime import build_message_history
from agent_services.agent.toolset_factory import execution_needs_agent_context
from agent_services.schemas.chat import ChatHistoryMessage, PromptProfile
from agent_services.schemas.stream import AgentStreamEvent, AgentStreamEventType
from agent_services.schemas.todo import TodoItem
from agent_services.agent.todo_tools import TodoStore


async def stream_chat_events(
    model: Model,
    *,
    site_id: str,
    message: str,
    prompt_profile: PromptProfile | None = None,
    history: list[ChatHistoryMessage] | None = None,
    retrieval_context: list[str] | None = None,
    skill_instructions: str | None = None,
    initial_todos: list[TodoItem] | None = None,
    toolsets: list[object] | None = None,
    todo_store: TodoStore | None = None,
) -> AsyncIterator[AgentStreamEvent]:
    system_prompt, user_message = build_prompt_parts(
        site_id,
        message,
        prompt_profile,
        retrieval_context,
        skill_instructions=skill_instructions,
        todos=initial_todos,
    )
    yield AgentStreamEvent(type=AgentStreamEventType.START, data={"siteId": site_id})

    run_kwargs: dict[str, object] = {}
    temperature = prompt_profile.temperature if prompt_profile else None
    if temperature is not None:
        run_kwargs["model_settings"] = ModelSettings(temperature=temperature)
    message_history = build_message_history(history)
    if message_history:
        run_kwargs["message_history"] = message_history

    side_events: list[AgentStreamEvent] = []

    async def map_tool_events(_ctx: object, events: object) -> None:
        async for event in events:  # type: ignore[union-attr]
            if isinstance(event, FunctionToolCallEvent):
                side_events.append(
                    AgentStreamEvent(
                        type=AgentStreamEventType.TOOL_START,
                        data={
                            "toolName": event.part.tool_name,
                            "toolCallId": event.tool_call_id,
                        },
                    )
                )
            elif isinstance(event, FunctionToolResultEvent):
                side_events.append(
                    AgentStreamEvent(
                        type=AgentStreamEventType.TOOL_RESULT,
                        data={
                            "toolName": event.part.tool_name,
                            "toolCallId": event.tool_call_id,
                        },
                    )
                )

    run_kwargs["event_stream_handler"] = map_tool_events

    agent = Agent(
        model,
        system_prompt=system_prompt,
        toolsets=toolsets or None,
    )

    reply = ""
    try:
        if execution_needs_agent_context(toolsets or []):
            async with agent:
                async for event in _emit_agent_stream(agent, user_message, run_kwargs, side_events):
                    if isinstance(event, AgentStreamEvent):
                        yield event
                    else:
                        reply = event
        else:
            async for event in _emit_agent_stream(agent, user_message, run_kwargs, side_events):
                if isinstance(event, AgentStreamEvent):
                    yield event
                else:
                    reply = event
    except Exception as exc:  # noqa: BLE001
        yield AgentStreamEvent(
            type=AgentStreamEventType.ERROR,
            data={"errorCode": "EXTERNAL_DEPENDENCY_BLOCKED", "message": str(exc)},
        )
        return

    if not reply:
        yield AgentStreamEvent(
            type=AgentStreamEventType.ERROR,
            data={
                "errorCode": "EXTERNAL_DEPENDENCY_BLOCKED",
                "message": "AI provider returned an empty response.",
            },
        )
        return

    done_data: dict[str, object] = {"reply": reply, "providerStatus": "LIVE"}
    if todo_store is not None:
        snapshot = todo_store.snapshot()
        if snapshot.items:
            done_data["todos"] = snapshot.to_api()
    yield AgentStreamEvent(type=AgentStreamEventType.DONE, data=done_data)


async def _emit_agent_stream(
    agent: Agent,
    user_message: str,
    run_kwargs: dict[str, object],
    side_events: list[AgentStreamEvent],
) -> AsyncIterator[AgentStreamEvent | str]:
    async with agent.run_stream(user_message, **run_kwargs) as stream:
        async for chunk in stream.stream_text(delta=True):
            while side_events:
                yield side_events.pop(0)
            if chunk:
                yield AgentStreamEvent(
                    type=AgentStreamEventType.TEXT_DELTA,
                    data={"text": chunk, "delta": chunk},
                )
        while side_events:
            yield side_events.pop(0)
        yield (await stream.get_output()).strip()
