"""已批准经验条目的生效接线（Issue #184 UoW-4）。

覆盖：组装段出现/顺序/L 号前缀、上限截断（条数＋正文总量＋单条超长）、
开关关闭回归、请求形状清洗，以及正式（run_chat）/流式（stream_chat_events）
两条组装路径——两者共用 assemble_harness，故路径级各验一条即可。
"""

import logging

from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.harness import assemble_harness
from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.config import Settings
from agent_services.schemas.chat import ChatRequest, ExperienceLessonItem


def _write_platform(tmp_path) -> None:
    (tmp_path / "background.md").write_text("PLATFORM-BACKGROUND", encoding="utf-8")
    (tmp_path / "guardrails.md").write_text("PLATFORM-GUARDRAIL", encoding="utf-8")
    (tmp_path / "tool_guidance.md").write_text("TOOL-GUIDANCE-TEXT", encoding="utf-8")


def _lesson(no: int, text: str) -> ExperienceLessonItem:
    return ExperienceLessonItem(lessonNo=no, lesson=text)


def test_verbatim_prompt_when_no_lessons_or_disabled(tmp_path) -> None:
    """硬验收：无条目/空列表/开关关闭时，system prompt 与现行逐字一致。"""
    _write_platform(tmp_path)
    baseline = assemble_harness(agent_id="1", message="hi", harness_dir=str(tmp_path))

    empty = assemble_harness(
        agent_id="1", message="hi", harness_dir=str(tmp_path), experience_lessons=[]
    )
    blank_only = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        experience_lessons=[_lesson(6, "   ")],
    )
    disabled = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        experience_lessons=[_lesson(6, "先枚举再逐份抽取")],
        experience_lessons_enabled=False,
    )

    assert empty.system_prompt == baseline.system_prompt
    assert blank_only.system_prompt == baseline.system_prompt
    assert disabled.system_prompt == baseline.system_prompt
    assert "# Experience lessons" not in baseline.system_prompt


def test_section_rendered_in_lesson_no_order_after_tool_guidance(tmp_path) -> None:
    _write_platform(tmp_path)
    harness = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        memories=["用户用日元结算"],
        experience_lessons=[
            _lesson(7, "工资默认实发口径"),
            _lesson(6, "先枚举再逐份抽取"),
        ],
    )
    prompt = harness.system_prompt
    section = "# Experience lessons\n\nL6. 先枚举再逐份抽取\nL7. 工资默认实发口径"
    assert section in prompt
    # 位置：tool_guidance 之后（且按组装现状排在 memories 段之后）。
    assert prompt.index("TOOL-GUIDANCE-TEXT") < prompt.index("# Experience lessons")
    assert prompt.index("# Long-term memory") < prompt.index("# Experience lessons")


def test_item_cap_keeps_newest_ten_and_logs_dropped(tmp_path, caplog) -> None:
    _write_platform(tmp_path)
    lessons = [_lesson(no, f"规则{no}") for no in range(6, 18)]  # L6..L17 共 12 条
    with caplog.at_level(logging.WARNING, logger="agent_services.agent.harness"):
        harness = assemble_harness(
            agent_id="1",
            message="hi",
            harness_dir=str(tmp_path),
            experience_lessons=lessons,
        )
    prompt = harness.system_prompt
    assert "L17. 规则17" in prompt
    assert "L8. 规则8" in prompt
    assert "L7. 规则7" not in prompt
    assert "L6. 规则6" not in prompt
    assert any(
        "experience lessons truncated" in record.getMessage()
        and "dropped 2" in record.getMessage()
        for record in caplog.records
    )


def test_char_cap_keeps_newest_within_budget_and_logs_dropped(tmp_path, caplog) -> None:
    _write_platform(tmp_path)
    text = "x" * 900  # 每条渲染行 904 字元：两条 1809 可入，第三条超 2000 总量
    lessons = [_lesson(6, text), _lesson(7, text), _lesson(8, text)]
    with caplog.at_level(logging.WARNING, logger="agent_services.agent.harness"):
        harness = assemble_harness(
            agent_id="1",
            message="hi",
            harness_dir=str(tmp_path),
            experience_lessons=lessons,
        )
    prompt = harness.system_prompt
    assert prompt.count("x" * 900) == 2
    assert f"L8. {text}" in prompt
    assert f"L7. {text}" in prompt
    assert f"L6. {text}" not in prompt
    assert any("dropped 1" in record.getMessage() for record in caplog.records)


def test_single_oversized_lesson_is_truncated_to_budget_and_logged(
    tmp_path, caplog
) -> None:
    _write_platform(tmp_path)
    with caplog.at_level(logging.WARNING, logger="agent_services.agent.harness"):
        harness = assemble_harness(
            agent_id="1",
            message="hi",
            harness_dir=str(tmp_path),
            experience_lessons=[_lesson(6, "y" * 5_000)],
        )
    prompt = harness.system_prompt
    assert "# Experience lessons" in prompt
    line = next(line for line in prompt.splitlines() if line.startswith("L6. "))
    assert line.endswith("[... truncated]")
    assert len(line) == 2_000
    assert any(
        "text_truncated=True" in record.getMessage() for record in caplog.records
    )


def test_chat_request_cleans_experience_lessons() -> None:
    request = ChatRequest.model_validate(
        {
            "agentId": "1",
            "message": "hi",
            "experienceLessons": [
                {"lessonNo": 6, "lesson": "  先枚举再逐份抽取  "},
                {"lessonNo": 7, "lesson": "   "},
            ],
        }
    )
    assert request.experience_lessons == [_lesson(6, "先枚举再逐份抽取")]

    blank = ChatRequest.model_validate(
        {
            "agentId": "1",
            "message": "hi",
            "experienceLessons": [{"lessonNo": 6, "lesson": "  "}],
        }
    )
    assert blank.experience_lessons is None

    absent = ChatRequest.model_validate({"agentId": "1", "message": "hi"})
    assert absent.experience_lessons is None


def test_settings_switch_defaults_on() -> None:
    assert Settings.model_fields["agent_experience_lessons_enabled"].default is True


async def test_run_chat_path_includes_section(tmp_path) -> None:
    _write_platform(tmp_path)
    seen: list[str] = []

    def capture(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        return ModelResponse(parts=[TextPart("captured")])

    outcome = await run_chat(
        FunctionModel(capture),
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        experience_lessons=[_lesson(6, "先枚举再逐份抽取")],
    )
    assert isinstance(outcome, ChatReply)
    assert "# Experience lessons" in "\n".join(seen)
    assert "L6. 先枚举再逐份抽取" in "\n".join(seen)


async def test_stream_path_includes_section_and_honors_switch(tmp_path) -> None:
    _write_platform(tmp_path)
    seen: list[str] = []

    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo):
        seen.append(repr(messages))
        yield "ok"

    async for _event in stream_chat_events(
        FunctionModel(stream_function=stream_fn),
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        experience_lessons=[_lesson(6, "先枚举再逐份抽取")],
    ):
        pass
    assert "L6. 先枚举再逐份抽取" in "\n".join(seen)

    seen.clear()

    async def stream_fn_off(messages: list[ModelMessage], _info: AgentInfo):
        seen.append(repr(messages))
        yield "ok"

    async for _event in stream_chat_events(
        FunctionModel(stream_function=stream_fn_off),
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        experience_lessons=[_lesson(6, "先枚举再逐份抽取")],
        experience_lessons_enabled=False,
    ):
        pass
    assert "# Experience lessons" not in "\n".join(seen)
