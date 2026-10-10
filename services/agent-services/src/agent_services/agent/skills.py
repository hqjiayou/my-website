"""Phase 5 / §20：Skills — 文件 SoT 指令模块（平台加载，非业务文案）。

- 目录：`{skills_dir}/{id}/SKILL.md`
- 可选 YAML frontmatter：`description` / `version`（`id` 以目录名为准）
- 请求只传 skill id；内容不接受前端直传
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from pathlib import Path

logger = logging.getLogger(__name__)

_SKILLS_ROOT = Path(__file__).resolve().parents[3] / "skills"
_FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?(.*)\Z", re.DOTALL)
_NAME_RE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_-]*$")

# skills_dir → (stamp, catalog)；stamp 为 (相对路径, mtime) 元组，避免 XOR 碰撞
_catalog_cache: dict[str, tuple[tuple[tuple[str, int], ...], dict[str, "SkillDefinition"]]] = {}


@dataclass(frozen=True)
class SkillDefinition:
    id: str
    body: str
    description: str = ""
    version: str | None = None


@dataclass(frozen=True)
class SkillResolution:
    applied_ids: tuple[str, ...]
    missing_ids: tuple[str, ...]
    instructions: str | None

    @property
    def has_missing(self) -> bool:
        return bool(self.missing_ids)


def default_skills_directory() -> str:
    """未设置 AGENT_SKILLS_DIR 时的目录。

    源码布局下 parents[3] 是仓库根。装进 site-packages 后这条路径不再存在，
    再试镜像里的 /app/skills。
    """
    if _SKILLS_ROOT.is_dir():
        return str(_SKILLS_ROOT)
    deployed = Path("/app/skills")
    if deployed.is_dir():
        return str(deployed)
    return str(_SKILLS_ROOT)


def clear_skills_cache() -> None:
    _catalog_cache.clear()


def _parse_frontmatter(raw: str) -> tuple[dict[str, str], str]:
    text = raw.replace("\r\n", "\n").replace("\r", "\n").strip()
    match = _FRONTMATTER_RE.match(text)
    if not match:
        return {}, text
    meta: dict[str, str] = {}
    for line in match.group(1).splitlines():
        line = line.strip()
        if not line or line.startswith("#") or ":" not in line:
            continue
        key, _, value = line.partition(":")
        meta[key.strip().lower()] = value.strip().strip("\"'")
    return meta, match.group(2).strip()


def _load_skill_file(skill_id: str, path: Path) -> SkillDefinition | None:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as exc:
        logger.warning("Failed to read skill %s: %s", skill_id, type(exc).__name__, exc_info=exc)
        return None
    meta, body = _parse_frontmatter(raw)
    if not body:
        logger.warning("Skill %s has empty body, skipped", skill_id)
        return None
    description = meta.get("description") or ""
    version = meta.get("version") or None
    return SkillDefinition(id=skill_id, body=body, description=description, version=version)


def _dir_mtime_ns(root: Path) -> int:
    try:
        return root.stat().st_mtime_ns
    except OSError:
        return 0


def _catalog_stamp(root: Path) -> tuple[tuple[str, int], ...]:
    """目录 + 各 SKILL.md 的稳定指纹（删除/改名也会变）。"""
    items: list[tuple[str, int]] = [("", _dir_mtime_ns(root))]
    try:
        children = sorted(root.iterdir(), key=lambda p: p.name)
    except OSError:
        return tuple(items)
    for child in children:
        skill_file = child / "SKILL.md"
        if skill_file.is_file():
            try:
                items.append((child.name, skill_file.stat().st_mtime_ns))
            except OSError:
                continue
    return tuple(items)


def _is_within(root: Path, path: Path) -> bool:
    """解析符号链接后仍须落在 skills 目录内，避免把目录外文件读进 system prompt。"""
    try:
        path.resolve().relative_to(root.resolve())
    except (OSError, ValueError):
        return False
    return True


def discover_skills(skills_dir: str) -> dict[str, SkillDefinition]:
    """按目录发现 skill；缓存随 skill 文件 mtime 失效。"""
    root = Path(skills_dir)
    if not root.is_dir():
        return {}
    stamp = _catalog_stamp(root)
    cached = _catalog_cache.get(skills_dir)
    if cached is not None and cached[0] == stamp:
        return cached[1]

    discovered: dict[str, SkillDefinition] = {}
    try:
        children = sorted(root.iterdir())
    except OSError as exc:
        logger.warning("Failed to list skills dir: %s", type(exc).__name__, exc_info=exc)
        return {}

    for child in children:
        if not child.is_dir() or not _NAME_RE.match(child.name):
            continue
        skill_file = child / "SKILL.md"
        if not skill_file.is_file():
            continue
        if not _is_within(root, skill_file):
            logger.warning("Skill %s escapes the skills directory, skipped", child.name)
            continue
        definition = _load_skill_file(child.name, skill_file)
        if definition is not None:
            discovered[child.name] = definition

    _catalog_cache[skills_dir] = (stamp, discovered)
    return discovered


def list_skill_catalog(skills_dir: str) -> list[SkillDefinition]:
    catalog = discover_skills(skills_dir)
    return [catalog[key] for key in sorted(catalog)]


def resolve_skills(
    *,
    skills_dir: str,
    requested_skill_ids: list[str] | None,
    default_skill_ids: list[str] | None,
) -> SkillResolution:
    """合并 default → requested，去重保序；未知 id 记入 missing（由调用方按策略处理）。"""
    catalog = discover_skills(skills_dir)
    ordered_ids: list[str] = []
    missing: list[str] = []
    seen_missing: set[str] = set()

    for skill_id in list(default_skill_ids or []) + list(requested_skill_ids or []):
        sid = (skill_id or "").strip()
        if not sid:
            continue
        if sid in catalog:
            if sid not in ordered_ids:
                ordered_ids.append(sid)
            continue
        if sid not in seen_missing:
            seen_missing.add(sid)
            missing.append(sid)

    # 默认 skill 配错时尽早告警（reject 策略下会导致全站 chat 400）
    if default_skill_ids:
        default_set = {s.strip() for s in default_skill_ids if s and s.strip()}
        for sid in missing:
            if sid in default_set:
                logger.warning(
                    "Default skill id %r not found under %s",
                    sid,
                    skills_dir,
                )

    if not ordered_ids:
        return SkillResolution(applied_ids=(), missing_ids=tuple(missing), instructions=None)

    sections = [
        f"## Skill: {skill_id}\n\n{catalog[skill_id].body}" for skill_id in ordered_ids
    ]
    return SkillResolution(
        applied_ids=tuple(ordered_ids),
        missing_ids=tuple(missing),
        instructions="\n\n".join(sections),
    )


def resolve_skill_instructions(
    *,
    skills_dir: str,
    requested_skill_ids: list[str] | None,
    default_skill_ids: list[str] | None,
) -> str | None:
    """兼容旧调用：仅返回 instructions 文本。"""
    return resolve_skills(
        skills_dir=skills_dir,
        requested_skill_ids=requested_skill_ids,
        default_skill_ids=default_skill_ids,
    ).instructions
