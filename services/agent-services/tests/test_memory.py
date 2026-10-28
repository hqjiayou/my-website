"""长期记忆：提示注入、抽取过滤，以及串流最终事件。"""

from pydantic import ValidationError
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.harness import assemble_harness
from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.schemas.chat import MAX_MEMORIES, MAX_MEMORY_CHARS, ChatRequest
from agent_services.schemas.stream import AgentStreamEventType


def _array_argument(info: AgentInfo) -> tuple[str, str]:
    tool = info.output_tools[0]
    schema = tool.parameters_json_schema
    properties = schema.get("properties") or {}
    for name, spec in properties.items():
        if isinstance(spec, dict) and spec.get("type") == "array":
            return tool.name, name
    if properties:
        return tool.name, next(iter(properties))
    raise AssertionError(f"extract tool has no arguments: {schema}")


def _model(facts: list[str] | None, calls: list[str], *, boom: bool = False) -> FunctionModel:
    def respond(_messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        if info.output_tools:
            calls.append("extract")
            if boom:
                raise RuntimeError("extract down")
            tool_name, arg_name = _array_argument(info)
            payload = [] if facts is None else facts
            return ModelResponse(
                parts=[ToolCallPart(tool_name=tool_name, args={arg_name: payload}, tool_call_id="extract")]
            )
        calls.append("chat")
        return ModelResponse(parts=[TextPart("assistant reply")])

    async def stream_fn(_messages: list[ModelMessage], _info: AgentInfo):
        calls.append("stream")
        yield "streamed reply"

    return FunctionModel(respond, stream_function=stream_fn)


def test_request_caps_memory_count_and_length() -> None:
    kept = ChatRequest.model_validate(
        {"agentId": "1", "message": "hi", "memories": ["  " + ("a" * (MAX_MEMORY_CHARS + 5)), "  tea  "]}
    )
    assert kept.memories is not None
    assert kept.memories[0] == "a" * MAX_MEMORY_CHARS
    assert kept.memories[1] == "tea"
    blank = ChatRequest.model_validate({"agentId": "1", "message": "hi", "memories": ["  ", ""]})
    assert blank.memories is None
    assert blank.memory_enabled is False
    try:
        ChatRequest.model_validate(
            {"agentId": "1", "message": "hi", "memories": [str(i) for i in range(MAX_MEMORIES + 1)]}
        )
    except ValidationError:
        return
    raise AssertionError("more than 20 memories must be rejected")


def test_harness_prompt_is_unchanged_without_memories(tmp_path) -> None:
    (tmp_path / "background.md").write_text("BACKGROUND", encoding="utf-8")
    bare = assemble_harness(agent_id="1", message="hi", harness_dir=str(tmp_path))
    empty = assemble_harness(agent_id="1", message="hi", harness_dir=str(tmp_path), memories=None)
    blank = assemble_harness(agent_id="1", message="hi", harness_dir=str(tmp_path), memories=["  ", ""])
    assert bare.system_prompt == empty.system_prompt == blank.system_prompt
    assert "Long-term memory" not in bare.system_prompt


def test_harness_adds_memory_section_only_when_present(tmp_path) -> None:
    (tmp_path / "background.md").write_text("BACKGROUND", encoding="utf-8")
    harness = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        memories=["User likes tea."],
    )
    prompt = harness.system_prompt
    assert "# Long-term memory" in prompt
    assert "User likes tea." in prompt
    assert "Do not recite them one by one." in prompt
    assert "follow this turn." in prompt
    assert prompt.index("# Long-term memory") < prompt.index("You are the assistant for site: 1.")


async def test_extract_returns_facts_on_the_reply() -> None:
    calls: list[str] = []
    outcome = await run_chat(
        _model(["User likes tea."], calls),
        agent_id="1",
        message="I like tea",
        memories=["User lives in Taipei."],
        memory_enabled=True,
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "assistant reply"
    assert outcome.extracted_memories == ["User likes tea."]
    assert calls == ["chat", "extract"]


async def test_extract_drops_exact_duplicates_of_existing() -> None:
    outcome = await run_chat(
        _model(["User likes tea.", "  User likes tea.  "], []),
        agent_id="1",
        message="tea again",
        memories=["User likes tea."],
        memory_enabled=True,
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "assistant reply"
    assert outcome.extracted_memories == []


async def test_extract_failure_keeps_the_reply_and_returns_empty() -> None:
    outcome = await run_chat(
        _model(None, [], boom=True),
        agent_id="1",
        message="hello",
        memory_enabled=True,
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "assistant reply"
    assert outcome.extracted_memories == []


async def test_memory_disabled_does_not_extract() -> None:
    calls: list[str] = []
    outcome = await run_chat(
        _model(["User likes tea."], calls),
        agent_id="1",
        message="I like tea",
        memories=["User likes tea."],
        memory_enabled=False,
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.extracted_memories == []
    assert calls == ["chat"]


async def test_extraction_switch_off_does_not_extract() -> None:
    calls: list[str] = []
    outcome = await run_chat(
        _model(["User likes tea."], calls),
        agent_id="1",
        message="I like tea",
        memory_enabled=True,
        memory_extraction_enabled=False,
    )
    assert isinstance(outcome, ChatReply)
    assert calls == ["chat"]
    assert outcome.extracted_memories == []


async def test_stream_done_carries_extracted_memories() -> None:
    calls: list[str] = []
    events = [
        event
        async for event in stream_chat_events(
            _model(["User lives in Taipei."], calls),
            agent_id="1",
            message="I live in Taipei",
            memory_enabled=True,
        )
    ]
    done = next(event for event in events if event.type == AgentStreamEventType.DONE)
    assert done.data["reply"] == "streamed reply"
    assert done.data["extractedMemories"] == ["User lives in Taipei."]
    assert done.data["usage"]["requests"] == 1
    assert "extract" in calls
