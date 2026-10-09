"""§20 Skills 平台化测试。"""

from pathlib import Path

from fastapi.testclient import TestClient
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.skills import (
    clear_skills_cache,
    discover_skills,
    resolve_skills,
)
from agent_services.app import create_app
from conftest import TOKEN_HEADER, make_settings


def _write_skill(root: Path, skill_id: str, body: str, *, frontmatter: str | None = None) -> None:
    skill_dir = root / skill_id
    skill_dir.mkdir()
    content = body
    if frontmatter:
        content = f"---\n{frontmatter}\n---\n\n{body}"
    (skill_dir / "SKILL.md").write_text(content, encoding="utf-8")


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


def test_unknown_skill_ids_are_tracked(tmp_path: Path) -> None:
    clear_skills_cache()
    resolution = resolve_skills(
        skills_dir=str(tmp_path),
        requested_skill_ids=["nope"],
        default_skill_ids=None,
    )
    assert resolution.instructions is None
    assert resolution.missing_ids == ("nope",)


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
    response = TestClient(app).post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello", "skills": ["missing-x"]},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["skillsApplied"] == ["demo"]
    assert data["skillsMissing"] == ["missing-x"]


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
        json={"siteId": "1", "message": "hello", "skills": ["nope"]},
    )
    assert response.status_code == 400
    assert response.json()["errorCode"] == "BAD_REQUEST"
    assert "nope" in response.json()["message"]
