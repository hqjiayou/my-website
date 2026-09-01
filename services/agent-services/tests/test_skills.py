"""Phase 5 skills 加载测试。"""

from pathlib import Path

from agent_services.agent.skills import resolve_skill_instructions


def test_resolve_skill_instructions_merges_request_and_defaults(tmp_path: Path) -> None:
    skill_dir = tmp_path / "demo"
    skill_dir.mkdir()
    (skill_dir / "SKILL.md").write_text("Do the demo thing.", encoding="utf-8")

    text = resolve_skill_instructions(
        skills_dir=str(tmp_path),
        requested_skill_ids=["demo"],
        default_skill_ids=["missing"],
    )
    assert text is not None
    assert "Do the demo thing." in text


def test_unknown_skill_ids_are_ignored(tmp_path: Path) -> None:
    assert (
        resolve_skill_instructions(
            skills_dir=str(tmp_path),
            requested_skill_ids=["nope"],
            default_skill_ids=None,
        )
        is None
    )
