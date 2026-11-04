"""Issue #195 确定性验收：references 登记、自选工具、catalog 注入与遥测合并。

真模型自选触发率不在 CI 内跑（沙箱无凭据）；这里只验机制：discover 登记、
load/read 工具口径与路径安全、开关关时组装逐字一致、开关开时 catalog 注入、
工厂门控与 skillsApplied 合并去重。
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path

from conftest import make_settings
from pydantic_ai.messages import ModelMessage, ToolReturnPart
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel

from agent_services.agent.harness import assemble_harness
from agent_services.agent.skill_tools import (
    REFERENCE_MAX_CHARS,
    SkillLoadCollector,
    build_skill_toolset,
    merge_skills_applied,
)
from agent_services.agent.skills import (
    clear_skills_cache,
    default_skills_directory,
    discover_skills,
    list_skill_catalog,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.config import Settings


def _write_skill(
    root: Path,
    skill_id: str,
    body: str,
    *,
    description: str = "Use for tests. Do not use elsewhere.",
    references: dict[str, str] | None = None,
) -> None:
    skill_dir = root / skill_id
    skill_dir.mkdir(parents=True)
    (skill_dir / "SKILL.md").write_text(
        f"---\nname: {skill_id}\ndescription: {description}\n---\n\n{body}\n",
        encoding="utf-8",
    )
    for rel, content in (references or {}).items():
        ref_path = skill_dir / "references" / rel
        ref_path.parent.mkdir(parents=True, exist_ok=True)
        ref_path.write_text(content, encoding="utf-8")


def _tool(toolset: object, name: str):  # noqa: ANN202 - 测试内取工具函数
    return toolset.tools[name].function  # type: ignore[attr-defined]


def test_discover_registers_references_sorted_files_only(tmp_path: Path) -> None:
    _write_skill(
        tmp_path,
        "alpha",
        "Alpha body.",
        references={"b.md": "B", "sub/a.md": "A", "top.txt": "T"},
    )
    _write_skill(tmp_path, "beta", "Beta body.")
    (tmp_path / "alpha" / "references" / "emptydir").mkdir()

    clear_skills_cache()
    catalog = discover_skills(str(tmp_path))
    assert catalog["alpha"].references == ("b.md", "sub/a.md", "top.txt")
    assert catalog["beta"].references == ()


def test_discover_registers_real_doc_coauthoring_reference() -> None:
    clear_skills_cache()
    catalog = discover_skills(default_skills_directory())
    assert catalog["doc-coauthoring"].references == ("section-templates.md",)
    assert catalog["cross-doc-aggregation"].references == ()


def test_load_skill_returns_body_and_records_once(tmp_path: Path) -> None:
    _write_skill(tmp_path, "alpha", "Alpha instructions.")
    clear_skills_cache()
    collector = SkillLoadCollector()
    toolset = build_skill_toolset(str(tmp_path), collector=collector)

    text = _tool(toolset, "load_skill")("alpha")
    assert text.startswith("## Skill: alpha")
    assert "Alpha instructions." in text
    assert collector.loaded_ids == ["alpha"]

    again = _tool(toolset, "load_skill")("alpha")
    assert "already loaded" in again
    assert collector.loaded_ids == ["alpha"]


def test_load_skill_preselected_is_not_reloaded(tmp_path: Path) -> None:
    _write_skill(tmp_path, "alpha", "Alpha instructions.")
    clear_skills_cache()
    collector = SkillLoadCollector()
    toolset = build_skill_toolset(
        str(tmp_path), collector=collector, preselected_ids=["alpha"]
    )
    text = _tool(toolset, "load_skill")("alpha")
    assert "already active" in text
    # 预选集由调用方计入 skillsApplied，工具侧不重复记账。
    assert collector.loaded_ids == []


def test_load_skill_unknown_id_returns_error_not_raise(tmp_path: Path) -> None:
    _write_skill(tmp_path, "alpha", "Alpha instructions.")
    clear_skills_cache()
    toolset = build_skill_toolset(str(tmp_path), collector=SkillLoadCollector())
    text = _tool(toolset, "load_skill")("nope")
    assert text.startswith("error: unknown skill id 'nope'")
    assert "alpha" in text


def test_load_skill_respects_instruction_budget(tmp_path: Path) -> None:
    _write_skill(tmp_path, "alpha", "x" * 500)
    clear_skills_cache()
    toolset = build_skill_toolset(
        str(tmp_path), collector=SkillLoadCollector(), max_instruction_chars=100
    )
    text = _tool(toolset, "load_skill")("alpha")
    assert "[... truncated]" in text
    assert len(text) <= 100 + len("[... truncated]")


def test_read_skill_reference_roundtrip_and_truncation(tmp_path: Path) -> None:
    _write_skill(
        tmp_path,
        "alpha",
        "Alpha body.",
        references={"guide.md": "short guide", "big.md": "y" * (REFERENCE_MAX_CHARS + 10)},
    )
    clear_skills_cache()
    toolset = build_skill_toolset(str(tmp_path), collector=SkillLoadCollector())
    read = _tool(toolset, "read_skill_reference")
    assert read("alpha", "guide.md") == "short guide"
    big = read("alpha", "big.md")
    assert big.startswith("y" * 100)
    assert big.endswith("[... truncated]")
    assert len(big) <= REFERENCE_MAX_CHARS + len("\n[... truncated]")


def test_read_skill_reference_rejects_traversal_and_unregistered(
    tmp_path: Path,
) -> None:
    _write_skill(
        tmp_path, "alpha", "Alpha body.", references={"guide.md": "guide"}
    )
    (tmp_path / "secret.md").write_text("secret", encoding="utf-8")
    clear_skills_cache()
    toolset = build_skill_toolset(str(tmp_path), collector=SkillLoadCollector())
    read = _tool(toolset, "read_skill_reference")
    for bad in ("../secret.md", "sub/../../secret.md", "/etc/passwd", "missing.md"):
        text = read("alpha", bad)
        assert text.startswith("error:"), f"{bad} should be rejected, got {text!r}"
        assert "secret" not in text or "not registered" in text
    assert read("nope", "guide.md").startswith("error: unknown skill id 'nope'")


def test_merge_skills_applied_orders_and_dedupes() -> None:
    assert merge_skills_applied(["orchestration", "alpha"], ["alpha", "beta"]) == [
        "orchestration",
        "alpha",
        "beta",
    ]
    assert merge_skills_applied(None, None) == []
    assert merge_skills_applied([" alpha "], [""]) == ["alpha"]


def test_self_select_default_off_in_settings() -> None:
    assert Settings.model_fields["agent_skills_self_select_enabled"].default is False


def test_harness_without_self_select_is_verbatim(tmp_path: Path) -> None:
    clear_skills_cache()
    catalog = list_skill_catalog(default_skills_directory())
    baseline = assemble_harness(agent_id="1", message="hi", harness_dir=str(tmp_path))
    disabled = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        skill_catalog=catalog,
        skills_self_select_enabled=False,
    )
    assert disabled.system_prompt == baseline.system_prompt
    assert "Skill catalog" not in baseline.system_prompt


def test_harness_catalog_section_when_enabled(tmp_path: Path) -> None:
    clear_skills_cache()
    catalog = list_skill_catalog(default_skills_directory())
    harness = assemble_harness(
        agent_id="1",
        message="hi",
        harness_dir=str(tmp_path),
        skill_catalog=catalog,
        skills_self_select_enabled=True,
    )
    prompt = harness.system_prompt
    assert "# Skill catalog" in prompt
    assert "load_skill" in prompt
    assert "read_skill_reference" in prompt
    # 每行一 skill；带 references 的标注数量，无 references 的不标注。
    assert "- doc-coauthoring — " in prompt
    assert "(references: 1)" in prompt
    coauthoring_line = next(
        line for line in prompt.splitlines() if line.startswith("- doc-coauthoring")
    )
    assert coauthoring_line.endswith("(references: 1)")
    aggregation_line = next(
        line
        for line in prompt.splitlines()
        if line.startswith("- cross-doc-aggregation")
    )
    assert "references:" not in aggregation_line


def _tool_names(toolsets: list[object]) -> set[str]:
    names: set[str] = set()
    for toolset in toolsets:
        tools = getattr(toolset, "tools", None)
        if isinstance(tools, dict):
            for key, tool in tools.items():
                raw = getattr(tool, "name", None) or key
                if isinstance(raw, str):
                    names.add(raw)
    return names


def test_factory_registers_skill_tools_only_with_collector(tmp_path: Path) -> None:
    settings = make_settings(business_api_base_url="", mcp_servers_json="")
    off = build_execution_toolsets(settings, "9", todo_store=TodoStore())
    assert "load_skill" not in _tool_names(off)
    assert "read_skill_reference" not in _tool_names(off)

    on = build_execution_toolsets(
        settings,
        "9",
        todo_store=TodoStore(),
        skills_dir=str(tmp_path),
        skill_load_collector=SkillLoadCollector(),
    )
    names = _tool_names(on)
    assert "load_skill" in names
    assert "read_skill_reference" in names


def _has_tool_return(messages: list[ModelMessage]) -> bool:
    return any(
        isinstance(part, ToolReturnPart)
        for message in messages
        for part in getattr(message, "parts", [])
    )


async def test_stream_done_merges_self_selected_skills(tmp_path: Path) -> None:
    _write_skill(tmp_path, "alpha", "Alpha instructions.")
    clear_skills_cache()
    collector = SkillLoadCollector()
    toolset = build_skill_toolset(str(tmp_path), collector=collector)

    async def stream_fn(
        messages: list[ModelMessage], _info: AgentInfo
    ) -> AsyncIterator[object]:
        if _has_tool_return(messages):
            yield "done"
            return
        yield {0: DeltaToolCall(name="load_skill", json_args='{"skill_id": "alpha"}')}

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            agent_id="1",
            message="do the alpha thing",
            toolsets=[toolset],
            skills_applied=["orchestration"],
            skill_catalog=list_skill_catalog(str(tmp_path)),
            skills_self_select_enabled=True,
            skill_load_collector=collector,
            harness_dir=str(tmp_path),
        )
    ]
    # START 在 run 之前发出，只带预选集；DONE 是合并自选后的最终集。
    assert events[0].data["skillsApplied"] == ["orchestration"]
    done = events[-1]
    assert done.type.value == "DONE"
    assert done.data["skillsApplied"] == ["orchestration", "alpha"]
