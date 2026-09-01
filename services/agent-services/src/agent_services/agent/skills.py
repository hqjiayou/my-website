"""Phase 5：Skills — 可复用指令模块（文件 SoT，由 Java 请求传入 skill id 激活）。"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

_SKILLS_ROOT = Path(__file__).resolve().parents[3] / "skills"


@lru_cache
def _discover_skills(skills_dir: str) -> dict[str, str]:
    root = Path(skills_dir)
    if not root.is_dir():
        return {}
    discovered: dict[str, str] = {}
    for child in sorted(root.iterdir()):
        if not child.is_dir():
            continue
        skill_file = child / "SKILL.md"
        if skill_file.is_file():
            discovered[child.name] = skill_file.read_text(encoding="utf-8").strip()
    return discovered


def resolve_skill_instructions(
    *,
    skills_dir: str,
    requested_skill_ids: list[str] | None,
    default_skill_ids: list[str] | None,
) -> str | None:
    """合并请求指定与默认 skill，去重保序；未知 id 忽略。"""
    catalog = _discover_skills(skills_dir)
    if not catalog:
        return None

    ordered_ids: list[str] = []
    for skill_id in (default_skill_ids or []) + (requested_skill_ids or []):
        if skill_id and skill_id not in ordered_ids and skill_id in catalog:
            ordered_ids.append(skill_id)
    if not ordered_ids:
        return None

    sections = [f"## Skill: {skill_id}\n\n{catalog[skill_id]}" for skill_id in ordered_ids]
    return "\n\n".join(sections)


def default_skills_directory() -> str:
    return str(_SKILLS_ROOT)
