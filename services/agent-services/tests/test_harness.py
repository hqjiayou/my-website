from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.toolsets.function import FunctionToolset
from pydantic_ai.usage import UsageLimits

from agent_services.agent.harness import assemble_harness
from agent_services.agent.runtime import ChatReply, RunBudget, run_chat
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.app import create_app
from agent_services.schemas.chat import ChatHistoryMessage, PromptProfile


class _FakeMcp:
    def __init__(self) -> None:
        self.url = "http://secret.example/mcp"


class _FakePrefixed:
    def __init__(self) -> None:
        self.prefix = "echo"
        self.wrapped = _FakeMcp()


def _budget() -> RunBudget:
    return RunBudget(
        usage_limits=UsageLimits(request_limit=3, tool_calls_limit=4, total_tokens_limit=None),
        tool_timeout_seconds=9,
        history_max_chars=100,
    )


def _write_platform(tmp_path) -> None:
    (tmp_path / "background.md").write_text("PLATFORM-BACKGROUND", encoding="utf-8")
    (tmp_path / "guardrails.md").write_text("PLATFORM-GUARDRAIL", encoding="utf-8")
    (tmp_path / "tool_guidance.md").write_text("Use tools for site facts.", encoding="utf-8")


def _site_tools() -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain
    def get_site_info() -> dict:
        """Look up the site."""
        leaked = "secret-arg"
        return {"x": leaked}

    return toolset.prefixed("site")


def test_assemble_lands_background_skills_mcp_and_hides_secrets(tmp_path) -> None:
    _write_platform(tmp_path)
    skill_ids = ["orchestration"]
    harness = assemble_harness(
        agent_id="9",
        message="  hi  ",
        prompt_profile=PromptProfile(systemPrompt="JAVA-PROMPT"),
        skill_instructions="SKILL-BODY",
        skill_ids=skill_ids,
        toolsets=[_site_tools(), _FakePrefixed()],
        budget=_budget(),
        skills_missing=["missing-x"],
        skills_max_instruction_chars=1000,
        history=[
            ChatHistoryMessage(role="USER", content="x" * 80),
            ChatHistoryMessage(role="ASSISTANT", content="y" * 80),
            ChatHistoryMessage(role="USER", content="KEEP"),
        ],
        harness_dir=str(tmp_path),
        locale="ja-JP",
    )
    skill_ids.append("other")

    assert harness.skill_ids == ("orchestration",)
    assert harness.skills_missing == ("missing-x",)
    assert harness.memory == (ChatHistoryMessage(role="USER", content="KEEP"),)
    assert [item.name for item in harness.tool_guidance] == ["site_get_site_info"]
    assert harness.user_message == "hi"
    assert harness.background == "PLATFORM-BACKGROUND"
    assert harness.mcp_servers == ("echo",)
    assert harness.guardrails.max_requests == 3
    assert harness.guardrails.max_tool_calls == 4
    assert harness.guardrails.tool_timeout_seconds == 9
    assert harness.guardrails.history_max_chars == 100
    assert harness.guardrails.skills_max_instruction_chars == 1000
    assert any(item.name == "site_get_site_info" and "Look up the site." in item.description for item in harness.tool_guidance)

    prompt = harness.system_prompt
    assert "PLATFORM-BACKGROUND" in prompt
    assert "PLATFORM-GUARDRAIL" in prompt
    assert "JAVA-PROMPT" in prompt
    assert "SKILL-BODY" in prompt
    assert "Use tools for site facts." in prompt
    assert "site_get_site_info" in prompt
    assert "echo" in prompt
    assert "- requests: 3" in prompt
    assert "x" * 80 not in prompt
    assert "Reply in this locale: ja-JP." in prompt
    leaked = prompt + " ".join(item.description for item in harness.tool_guidance)
    assert "secret-arg" not in leaked
    assert "secret.example" not in leaked


def test_long_background_is_marked_truncated(tmp_path) -> None:
    (tmp_path / "background.md").write_text("A" * 4_005, encoding="utf-8")
    harness = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
    )
    assert harness.background.endswith("[... truncated]")
    assert harness.background.startswith("A" * 100)
    assert len(harness.background) > 4_000


def test_long_background_truncation_logs_a_warning(tmp_path, caplog) -> None:
    (tmp_path / "background.md").write_text("A" * 4_005, encoding="utf-8")
    with caplog.at_level("WARNING", logger="agent_services.agent.harness"):
        harness = assemble_harness(
            agent_id="1",
            message="hi",
            harness_dir=str(tmp_path),
        )
    assert harness.background.endswith("[... truncated]")
    assert len(harness.background) > 4_000
    assert any(
        "background.md" in record.getMessage() and "truncated" in record.getMessage()
        for record in caplog.records
    )


def test_health_reports_which_harness_files_exist(tmp_path) -> None:
    present = TestClient(create_app(make_settings())).get("/internal/v1/health", headers=TOKEN_HEADER).json()
    assert present["harness"] == {"background": True, "guardrails": True, "toolGuidance": True}

    empty = TestClient(create_app(make_settings(agent_harness_dir=str(tmp_path)))).get(
        "/internal/v1/health", headers=TOKEN_HEADER
    ).json()
    assert empty["harness"] == {"background": False, "guardrails": False, "toolGuidance": False}


async def test_run_chat_sends_trimmed_memory_to_the_model(tmp_path) -> None:
    _write_platform(tmp_path)
    seen: list[str] = []

    def capture(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        return ModelResponse(parts=[TextPart("captured")])

    outcome = await run_chat(
        FunctionModel(capture),
        agent_id="1",
        message="now",
        history=[
            ChatHistoryMessage(role="USER", content="DROP-ME"),
            ChatHistoryMessage(role="USER", content="KEEP-ME"),
        ],
        budget=RunBudget(history_max_chars=7),
        harness_dir=str(tmp_path),
    )
    assert isinstance(outcome, ChatReply)
    assert len(seen) == 2
    assert "DROP-ME" in seen[0]
    assert "KEEP-ME" not in seen[0]
    assert "KEEP-ME" in seen[1]
    assert "DROP-ME" not in seen[1]


async def test_run_chat_sends_harness_background_to_the_model(tmp_path) -> None:
    _write_platform(tmp_path)
    seen: list[str] = []

    def capture(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        return ModelResponse(parts=[TextPart("captured")])

    outcome = await run_chat(
        FunctionModel(capture),
        agent_id="7",
        message="hi",
        prompt_profile=PromptProfile(systemPrompt="TRUSTED-JAVA-PROMPT"),
        skill_instructions="SKILL-BODY",
        skill_ids=["orchestration"],
        harness_dir=str(tmp_path),
    )
    assert isinstance(outcome, ChatReply)
    blob = "\n".join(seen)
    assert "PLATFORM-BACKGROUND" in blob
    assert "TRUSTED-JAVA-PROMPT" in blob
    assert "SKILL-BODY" in blob


async def test_stream_sends_harness_background_to_the_model(tmp_path) -> None:
    (tmp_path / "background.md").write_text("STREAM-BACKGROUND", encoding="utf-8")
    seen: list[str] = []

    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo):
        seen.append(repr(messages))
        yield "ok"

    events = [
        event.type.value
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            agent_id="1",
            message="hi",
            prompt_profile=PromptProfile(systemPrompt="JAVA-PROMPT"),
            harness_dir=str(tmp_path),
        )
    ]

    blob = "\n".join(seen)
    assert "STREAM-BACKGROUND" in blob
    assert "JAVA-PROMPT" in blob
    assert "DONE" in events
