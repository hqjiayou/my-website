"""Chat 执行：Pydantic AI Agent 运行（Phase 2 起支持 MULTI-TURN）。

Python 不保存会话状态（stateless between business requests）；
历史由 Java 会话真相源加载后随请求传入，转换为 Pydantic AI message_history。
"""

from dataclasses import dataclass

from pydantic_ai import Agent
from pydantic_ai.messages import ModelMessage, ModelRequest, ModelResponse, TextPart, UserPromptPart
from pydantic_ai.models import Model
from pydantic_ai.settings import ModelSettings

from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.agent.toolset_factory import execution_needs_agent_context
from agent_services.schemas.chat import ChatHistoryMessage, PromptProfile
from agent_services.schemas.todo import TodoItem


@dataclass(frozen=True)
class ChatReply:
    reply: str


@dataclass(frozen=True)
class ChatBlocked:
    message: str


ChatOutcome = ChatReply | ChatBlocked


def build_message_history(history: list[ChatHistoryMessage] | None) -> list[ModelMessage]:
    """Java 传入的扁平历史 → Pydantic AI ModelMessage；顺序保持原样。"""
    if not history:
        return []
    messages: list[ModelMessage] = []
    for item in history:
        if item.role == "USER":
            messages.append(ModelRequest(parts=[UserPromptPart(content=item.content)]))
        else:
            messages.append(ModelResponse(parts=[TextPart(content=item.content)]))
    return messages


async def run_chat(
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
) -> ChatOutcome:
    system_prompt, user_message = build_prompt_parts(
        site_id,
        message,
        prompt_profile,
        retrieval_context,
        skill_instructions=skill_instructions,
        todos=initial_todos,
    )

    run_kwargs: dict[str, object] = {}
    temperature = prompt_profile.temperature if prompt_profile else None
    if temperature is not None:
        run_kwargs["model_settings"] = ModelSettings(temperature=temperature)
    message_history = build_message_history(history)
    if message_history:
        run_kwargs["message_history"] = message_history

    try:
        agent = Agent(
            model,
            system_prompt=system_prompt,
            toolsets=toolsets or None,
        )
        if execution_needs_agent_context(toolsets or []):
            async with agent:
                result = await agent.run(user_message, **run_kwargs)
        else:
            result = await agent.run(user_message, **run_kwargs)
    except Exception as exc:  # noqa: BLE001 — Provider 调用失败 → 诚实 503，不伪造回复
        return ChatBlocked(message=f"AI provider call failed: {exc}")

    text = str(result.output).strip()
    if not text:
        return ChatBlocked(message="AI provider returned an empty response.")
    return ChatReply(reply=text)
