"""§20 Skills 平台化测试。"""

from pathlib import Path

import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic import ValidationError
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.agent.skills import (
    clear_skills_cache,
    default_skills_directory,
    discover_skills,
    limit_skill_instructions,
    resolve_skills,
)
from agent_services.app import create_app
from agent_services.config import Settings


def _write_skill(root: Path, skill_id: str, body: str, *, frontmatter: str | None = None) -> None:
    skill_dir = root / skill_id
    skill_dir.mkdir()
    content = body
    if frontmatter:
        content = f"---\n{frontmatter}\n---\n\n{body}"
    (skill_dir / "SKILL.md").write_text(content, encoding="utf-8")


def test_default_skills_directory_points_at_an_existing_catalog() -> None:
    root = Path(default_skills_directory())
    assert (root / "orchestration" / "SKILL.md").is_file()


def test_resolve_skill_instructions_merges_request_and_defaults(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "demo", "Do the demo thing.")

    resolution = resolve_skills(
        skills_dir=str(tmp_path),
        requested_skill_ids=["demo"],
        default_skill_ids=["missing"],
    )
    assert resolution.instructions is not None
    assert "Do the demo thing." in resolution.instructions
    assert resolution.applied_ids == ("demo",)
    assert resolution.missing_ids == ("missing",)


def test_explicit_skill_list_skips_process_defaults() -> None:
    from agent_services.api.internal import default_skill_ids_for_request

    assert default_skill_ids_for_request(None, "orchestration") == ["orchestration"]
    assert default_skill_ids_for_request([], "orchestration") is None
    assert default_skill_ids_for_request(["demo"], "orchestration") is None


def test_unknown_skill_ids_are_tracked(tmp_path: Path) -> None:
    clear_skills_cache()
    resolution = resolve_skills(
        skills_dir=str(tmp_path),
        requested_skill_ids=["nope"],
        default_skill_ids=None,
    )
    assert resolution.instructions is None
    assert resolution.missing_ids == ("nope",)


def test_symlink_outside_skills_dir_is_skipped(tmp_path: Path) -> None:
    clear_skills_cache()
    outside = tmp_path / "outside"
    outside.mkdir()
    (outside / "SKILL.md").write_text("SECRET OUTSIDE", encoding="utf-8")
    skills = tmp_path / "skills"
    skills.mkdir()
    try:
        (skills / "evil").symlink_to(outside, target_is_directory=True)
    except OSError:
        return
    catalog = discover_skills(str(skills))
    assert "evil" not in catalog


def test_frontmatter_accepts_crlf(tmp_path: Path) -> None:
    clear_skills_cache()
    skill_dir = tmp_path / "alpha"
    skill_dir.mkdir()
    (skill_dir / "SKILL.md").write_bytes(
        b'---\r\ndescription: Alpha skill\r\nversion: "2"\r\n---\r\n\r\nBody text here.\r\n'
    )
    catalog = discover_skills(str(tmp_path))
    assert catalog["alpha"].description == "Alpha skill"
    assert catalog["alpha"].version == "2"
    assert catalog["alpha"].body == "Body text here."


def test_frontmatter_parsed_into_catalog(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(
        tmp_path,
        "alpha",
        "Body text here.",
        frontmatter='description: Alpha skill\nversion: "2"',
    )
    catalog = discover_skills(str(tmp_path))
    assert catalog["alpha"].description == "Alpha skill"
    assert catalog["alpha"].version == "2"
    assert "Body text here." in catalog["alpha"].body
    assert "---" not in catalog["alpha"].body


def test_skills_catalog_endpoint(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(
        tmp_path,
        "alpha",
        "Body",
        frontmatter="description: Alpha\nversion: 1",
    )
    app = create_app(
        settings=make_settings(ai_model="test", agent_skills_dir=str(tmp_path), agent_default_skills=""),
        model_handle=ModelHandle(model=TestModel(), spec="test", blocked_reason=None),
    )
    body = TestClient(app).get("/internal/v1/skills", headers=TOKEN_HEADER).json()
    assert body["ok"] is True
    assert body["data"]["skills"][0]["id"] == "alpha"
    assert body["data"]["skills"][0]["description"] == "Alpha"
    # 自选开关随目录回报进程实际状态：默认关（#214 UoW-6 货架页展示用）。
    assert body["data"]["skills_self_select_enabled"] is False


def test_skills_catalog_endpoint_reports_self_select_enabled(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "alpha", "Body", frontmatter="description: Alpha")
    app = create_app(
        settings=make_settings(
            ai_model="test",
            agent_skills_dir=str(tmp_path),
            agent_default_skills="",
            agent_skills_self_select_enabled=True,
        ),
        model_handle=ModelHandle(model=TestModel(), spec="test", blocked_reason=None),
    )
    body = TestClient(app).get("/internal/v1/skills", headers=TOKEN_HEADER).json()
    assert body["data"]["skills_self_select_enabled"] is True


def test_chat_includes_skills_applied_and_missing(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "demo", "Demo body")
    app = create_app(
        settings=make_settings(
            ai_model="test",
            agent_skills_dir=str(tmp_path),
            agent_default_skills="demo",
            agent_skills_unknown_policy="ignore",
        ),
        model_handle=ModelHandle(
            model=TestModel(custom_output_text="hi"),
            spec="test",
            blocked_reason=None,
        ),
    )
    explicit = TestClient(app).post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "message": "hello", "skills": ["missing-x"]},
    )
    assert explicit.status_code == 200
    explicit_data = explicit.json()["data"]
    assert explicit_data["skillsApplied"] == []
    assert explicit_data["skillsMissing"] == ["missing-x"]

    omitted = TestClient(app).post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "message": "hello"},
    )
    assert omitted.status_code == 200
    assert omitted.json()["data"]["skillsApplied"] == ["demo"]


def test_reject_unknown_skills_returns_400(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "demo", "Demo body")
    app = create_app(
        settings=make_settings(
            ai_model="test",
            agent_skills_dir=str(tmp_path),
            agent_default_skills="",
            agent_skills_unknown_policy="reject",
        ),
        model_handle=ModelHandle(
            model=TestModel(custom_output_text="hi"),
            spec="test",
            blocked_reason=None,
        ),
    )
    response = TestClient(app).post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "message": "hello", "skills": ["nope"]},
    )
    assert response.status_code == 400
    assert response.json()["errorCode"] == "BAD_REQUEST"
    assert "nope" in response.json()["message"]


def test_skill_instruction_budget_under_limit_matches_resolved_text(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "one", "Do one.")
    _write_skill(tmp_path, "two", "Do two.")
    resolution = resolve_skills(
        skills_dir=str(tmp_path),
        requested_skill_ids=["one", "two"],
        default_skill_ids=None,
    )
    raw = resolution.instructions
    assert raw is not None
    assert limit_skill_instructions(raw, 12000) == raw
    plain, user = build_prompt_parts("1", "  hi  ", skill_instructions=raw)
    capped, capped_user = build_prompt_parts(
        "1", "  hi  ", skill_instructions=raw, skills_max_instruction_chars=12000
    )
    assert capped == plain
    assert capped_user == user == "hi"
    assert "# Active skills" in capped
    assert "Do one." in capped and "Do two." in capped
    assert "[skills truncated:" not in capped
    assert "[... truncated]" not in capped


def test_skill_instruction_budget_skips_sections_that_do_not_fit() -> None:
    first = "## Skill: one\n\n" + ("A" * 10)
    second = "## Skill: two\n\n" + ("B" * 40)
    third = "## Skill: three\n\n" + ("C" * 5)
    max_chars = len(first) + 2 + len(third)
    assert len(first) + 2 + len(second) > max_chars
    raw = "\n\n".join([first, second, third])
    text = limit_skill_instructions(raw, max_chars)
    assert text == (
        f"{first}\n\n{third}\n[skills truncated: 1 skill(s) omitted due to instruction budget]"
    )
    assert "B" * 40 not in text
    assert "[... truncated]" not in text
    system, _ = build_prompt_parts("1", "hi", skill_instructions=raw, skills_max_instruction_chars=max_chars)
    assert text in system


def test_oversized_first_skill_is_truncated_without_splitting_later_ones() -> None:
    first = "## Skill: big\n\n" + ("B" * 50)
    second = "## Skill: tiny\n\nTINY"
    max_chars = 12
    text = limit_skill_instructions(f"{first}\n\n{second}", max_chars)
    assert text == (
        first[:max_chars]
        + "[... truncated]\n"
        "[skills truncated: 1 skill(s) omitted due to instruction budget]"
    )
    assert "TINY" not in text
    assert "## Skill: tiny" not in text

    only = limit_skill_instructions(first, max_chars)
    assert only == first[:max_chars] + "[... truncated]"
    assert "skill(s) omitted" not in only


def test_invalid_skills_instruction_budget_fails_startup(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    for value in (0, -3):
        with pytest.raises(RuntimeError, match="AGENT_SKILLS_MAX_INSTRUCTION_CHARS"):
            create_app(make_settings(agent_skills_max_instruction_chars=value))
    for value in ("nope", 1.5):
        with pytest.raises(ValidationError):
            create_app(make_settings(agent_skills_max_instruction_chars=value))
    # 部署實際路徑是環境變數字面量：非整數一律在 Settings 建構期失敗。
    monkeypatch.setenv("AGENT_SKILLS_MAX_INSTRUCTION_CHARS", "true")
    with pytest.raises(ValidationError):
        Settings()


def test_chat_applies_skill_instruction_budget(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "big", "B" * 80)
    _write_skill(tmp_path, "tiny", "TINY-BODY")
    seen: list[str] = []

    def model_fn(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        return ModelResponse(parts=[TextPart("ok")])

    app = create_app(
        settings=make_settings(
            ai_model="test",
            agent_skills_dir=str(tmp_path),
            agent_default_skills="",
            agent_skills_max_instruction_chars=30,
        ),
        model_handle=ModelHandle(model=FunctionModel(model_fn), spec="test", blocked_reason=None),
    )
    response = TestClient(app).post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "message": "hello", "skills": ["big", "tiny"]},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["skillsApplied"] == ["big", "tiny"]
    blob = "\n".join(seen)
    assert "[... truncated]" in blob
    assert "[skills truncated: 1 skill(s) omitted due to instruction budget]" in blob
    assert "TINY-BODY" not in blob
    assert "## Skill: tiny" not in blob


def test_standard_skill_exposes_optional_fields(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(
        tmp_path,
        "pdf-tools",
        "Extract tables.",
        frontmatter=(
            "name: pdf-tools\n"
            "description: Extract tables from PDFs.\n"
            "license: Apache-2.0\n"
            "compatibility: Requires poppler\n"
            "metadata:\n"
            "  author: platform\n"
            '  version: "1"\n'
            'allowed-tools: "Bash(python:*) Read"\n'
        ),
    )
    skill = discover_skills(str(tmp_path))["pdf-tools"]
    assert skill.id == "pdf-tools"
    assert skill.description == "Extract tables from PDFs."
    assert skill.license == "Apache-2.0"
    assert skill.compatibility == "Requires poppler"
    assert skill.metadata == {"author": "platform", "version": "1"}
    assert skill.allowed_tools == ("Bash(python:*)", "Read")
    assert skill.version is None


def test_skill_name_mismatch_is_skipped(tmp_path: Path, caplog: pytest.LogCaptureFixture) -> None:
    clear_skills_cache()
    _write_skill(
        tmp_path,
        "good-name",
        "Body.",
        frontmatter="name: other-name\ndescription: Other\n",
    )
    with caplog.at_level("WARNING"):
        catalog = discover_skills(str(tmp_path))
    assert "good-name" not in catalog
    assert any("does not match" in record.message for record in caplog.records)


def test_invalid_skill_name_is_skipped(tmp_path: Path, caplog: pytest.LogCaptureFixture) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "bad_name", "Body.", frontmatter="name: bad_name\ndescription: Nope\n")
    with caplog.at_level("WARNING"):
        catalog = discover_skills(str(tmp_path))
    assert catalog == {}
    assert any("does not match name rules" in record.message for record in caplog.records)


def test_missing_name_falls_back_to_directory(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(tmp_path, "legacy-skill", "Legacy body.", frontmatter="description: Legacy\n")
    skill = discover_skills(str(tmp_path))["legacy-skill"]
    assert skill.id == "legacy-skill"
    assert skill.description == "Legacy"


def test_missing_description_is_derived_from_first_paragraph(
    tmp_path: Path, caplog: pytest.LogCaptureFixture
) -> None:
    clear_skills_cache()
    _write_skill(
        tmp_path,
        "notes",
        "First paragraph stays.\n\nSecond paragraph does not.",
        frontmatter="name: notes\n",
    )
    with caplog.at_level("INFO"):
        skill = discover_skills(str(tmp_path))["notes"]
    assert skill.description == "First paragraph stays."
    assert any("derived from body" in record.message for record in caplog.records)


def test_overlong_description_is_truncated(tmp_path: Path, caplog: pytest.LogCaptureFixture) -> None:
    clear_skills_cache()
    long = "x" * 1100
    _write_skill(
        tmp_path,
        "long-text",
        "Body.",
        frontmatter=f"name: long-text\ndescription: {long}\n",
    )
    with caplog.at_level("WARNING"):
        skill = discover_skills(str(tmp_path))["long-text"]
    assert skill.description == long[:1024]
    assert len(skill.description) == 1024
    assert any("truncated" in record.message for record in caplog.records)


def test_allowed_tools_yaml_list_is_parsed(tmp_path: Path) -> None:
    clear_skills_cache()
    _write_skill(
        tmp_path,
        "tool-list",
        "Use the listed tools.",
        frontmatter=(
            "name: tool-list\n"
            "description: Lists tools.\n"
            "allowed-tools:\n"
            "  - Bash\n"
            "  - Read\n"
        ),
    )
    skill = discover_skills(str(tmp_path))["tool-list"]
    assert skill.allowed_tools == ("Bash", "Read")
