"""Phase 5 / §20：Skills — 文件 SoT 指令模块（平台加载，非业务文案）。

目录对齐 Agent Skills 开放标准：``{skills_dir}/{name}/SKILL.md``。``id`` 仍是目录名。
Frontmatter 用 PyYAML ``safe_load`` 解析（顶层键按小写匹配，兼容旧文件大小写）。

- ``name``：``^[a-z0-9]+(-[a-z0-9]+)*$`` 且不超过 64。写出时必须与目录名一致，否则跳过并 warning。
  未写则回退目录名（旧文件兼容）；目录名不合格式同样跳过并 warning。
- ``description``：不超过 1024。缺失时用正文第一个非空段落派生（截到 1024）并记 info；超长截断并 warning。
- 可选：``license``、``compatibility``、``metadata``（字符串映射）、``allowed-tools``（实验性，空格分隔或字符串列表）。
  ``allowed-tools`` 只解析进 catalog。本包不按它做工具门控，也不改工具注册。
- ``version``：继续解析，供旧 skill 使用。它不是开放标准字段。
- ``references/`` 子目录（Issue #195）：discover 时只登记文件相对路径清单
  （相对 ``references/``、排序），正文不预读；内容由 ``skill_tools`` 的
  ``read_skill_reference`` 在开关开启时按需取用。

请求只传 skill id；内容不接受前端直传。
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any

import yaml

logger = logging.getLogger(__name__)

_SKILLS_ROOT = Path(__file__).resolve().parents[3] / "skills"
_FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?(.*)\Z", re.DOTALL)
_SKILL_NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
_SKILL_NAME_MAX = 64
_DESCRIPTION_MAX = 1024
_SKILL_SECTION_SPLIT = re.compile(r"\n\n(?=## Skill: )")
_SECTION_TRUNCATED_MARK = "[... truncated]"

# skills_dir → (stamp, catalog)；stamp 为 (相对路径, mtime) 元组，避免 XOR 碰撞
_catalog_cache: dict[str, tuple[tuple[tuple[str, int], ...], dict[str, "SkillDefinition"]]] = {}


@dataclass(frozen=True)
class SkillDefinition:
    id: str
    body: str
    description: str = ""
    version: str | None = None
    license: str | None = None
    compatibility: str | None = None
    metadata: dict[str, str] = field(default_factory=dict)
    allowed_tools: tuple[str, ...] = ()
    # references/ 下已登记的文件相对路径（相对 references/，正斜杠，排序）。
    # 只登记清单不读内容；按需读取在 skill_tools，路径以本清单为准入名单。
    references: tuple[str, ...] = ()


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


def _valid_skill_name(name: str) -> bool:
    return len(name) <= _SKILL_NAME_MAX and _SKILL_NAME_RE.fullmatch(name) is not None


def _parse_frontmatter(raw: str) -> tuple[dict[str, Any] | None, str]:
    """返回 (meta, body)。meta 为 None 表示 frontmatter 不是合法 YAML，调用方跳过该 skill。"""
    text = raw.replace("\r\n", "\n").replace("\r", "\n").strip()
    match = _FRONTMATTER_RE.match(text)
    if not match:
        return {}, text
    try:
        loaded = yaml.safe_load(match.group(1))
    except yaml.YAMLError:
        return None, match.group(2).strip()
    if loaded is None:
        return {}, match.group(2).strip()
    if not isinstance(loaded, dict):
        return None, match.group(2).strip()
    meta: dict[str, Any] = {}
    for key, value in loaded.items():
        if isinstance(key, str):
            meta[key.strip().lower()] = value
    return meta, match.group(2).strip()


def _optional_str(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text or None


def _legacy_version(value: object) -> str | None:
    if value is None:
        return None
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        text = value.strip()
        return text or None
    if isinstance(value, (int, float)):
        return str(value)
    return None


def _metadata_map(skill_id: str, value: object) -> dict[str, str]:
    if value is None:
        return {}
    if not isinstance(value, dict):
        logger.warning("Skill %s metadata is not a mapping, ignored", skill_id)
        return {}
    mapped: dict[str, str] = {}
    for key, item in value.items():
        if not isinstance(key, str):
            logger.warning("Skill %s metadata key is not a string, skipped", skill_id)
            continue
        if isinstance(item, str):
            mapped[key] = item
        elif isinstance(item, bool):
            mapped[key] = "true" if item else "false"
        elif isinstance(item, (int, float)):
            mapped[key] = str(item)
        else:
            logger.warning("Skill %s metadata %r is not a string, skipped", skill_id, key)
    return mapped


def _allowed_tools(skill_id: str, value: object) -> tuple[str, ...]:
    if value is None:
        return ()
    if isinstance(value, str):
        return tuple(part for part in value.split() if part)
    if isinstance(value, list):
        tools: list[str] = []
        for item in value:
            if isinstance(item, str) and item.strip():
                tools.append(item.strip())
            else:
                logger.warning("Skill %s allowed-tools entry is not a string, skipped", skill_id)
        return tuple(tools)
    logger.warning("Skill %s allowed-tools is not a string or list, ignored", skill_id)
    return ()


def _first_paragraph(body: str) -> str:
    for paragraph in re.split(r"\n[ \t]*\n", body.strip()):
        text = paragraph.strip()
        if text:
            return text
    return ""


def _fit_description(skill_id: str, text: str, *, derived: bool) -> str:
    if derived:
        logger.info("Skill %s description derived from body", skill_id)
    if len(text) > _DESCRIPTION_MAX:
        logger.warning("Skill %s description exceeds 1024 characters, truncated", skill_id)
        return text[:_DESCRIPTION_MAX]
    return text


def _load_skill_file(skill_id: str, path: Path) -> SkillDefinition | None:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as exc:
        logger.warning("Failed to read skill %s: %s", skill_id, type(exc).__name__, exc_info=exc)
        return None
    meta, body = _parse_frontmatter(raw)
    if meta is None:
        logger.warning("Skill %s frontmatter is not valid YAML, skipped", skill_id)
        return None
    if not body:
        logger.warning("Skill %s has empty body, skipped", skill_id)
        return None

    if "name" in meta and meta.get("name") is not None:
        raw_name = meta.get("name")
        name = raw_name.strip() if isinstance(raw_name, str) else ""
        if not isinstance(raw_name, str) or name != skill_id or not _valid_skill_name(name):
            logger.warning(
                "Skill %s frontmatter name %r does not match the directory or name rules, skipped",
                skill_id,
                raw_name if isinstance(raw_name, str) else type(raw_name).__name__,
            )
            return None

    raw_description = meta.get("description")
    if isinstance(raw_description, str) and raw_description.strip():
        description = _fit_description(skill_id, raw_description.strip(), derived=False)
    else:
        if raw_description is not None and not isinstance(raw_description, str):
            logger.warning("Skill %s description is not a string, deriving from body", skill_id)
        description = _fit_description(skill_id, _first_paragraph(body), derived=True)

    return SkillDefinition(
        id=skill_id,
        body=body,
        description=description,
        version=_legacy_version(meta.get("version")),
        license=_optional_str(meta.get("license")),
        compatibility=_optional_str(meta.get("compatibility")),
        metadata=_metadata_map(skill_id, meta.get("metadata")),
        allowed_tools=_allowed_tools(skill_id, meta.get("allowed-tools")),
    )


def _dir_mtime_ns(root: Path) -> int:
    try:
        return root.stat().st_mtime_ns
    except OSError:
        return 0


def _catalog_stamp(root: Path) -> tuple[tuple[str, int], ...]:
    """目录 + 各 SKILL.md 的稳定指纹（删除/改名也会变）。

    Issue #195 起 references/ 下文件也进指纹：清单是 discover 的登记结果，
    新增/删除/改名 reference 必须让缓存失效，否则登记与磁盘漂移。
    """
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
        refs_dir = child / "references"
        if refs_dir.is_dir():
            for ref_file in sorted(refs_dir.rglob("*")):
                if not ref_file.is_file():
                    continue
                try:
                    rel = ref_file.relative_to(child).as_posix()
                    items.append((f"{child.name}/{rel}", ref_file.stat().st_mtime_ns))
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


def _skill_references(root: Path, skill_dir_path: Path) -> tuple[str, ...]:
    """登记 references/ 下文件的相对路径（相对 references/、正斜杠、排序）。

    只登记文件本身；符号链接解析后仍须落在 skills 根内——这份清单就是
    read_skill_reference 的准入名单，目录外文件不得借登记混进来。
    """
    refs_dir = skill_dir_path / "references"
    if not refs_dir.is_dir():
        return ()
    found: list[str] = []
    for path in sorted(refs_dir.rglob("*")):
        if not path.is_file():
            continue
        if not _is_within(root, path):
            logger.warning(
                "Skill reference %s escapes the skills directory, not registered", path
            )
            continue
        found.append(path.relative_to(refs_dir).as_posix())
    return tuple(found)


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
        if not child.is_dir():
            continue
        skill_file = child / "SKILL.md"
        if not skill_file.is_file():
            continue
        if not _valid_skill_name(child.name):
            logger.warning("Skill directory %s does not match name rules, skipped", child.name)
            continue
        if not _is_within(root, skill_file):
            logger.warning("Skill %s escapes the skills directory, skipped", child.name)
            continue
        definition = _load_skill_file(child.name, skill_file)
        if definition is not None:
            discovered[child.name] = replace(
                definition, references=_skill_references(root, child)
            )

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


def _omitted_skills_mark(omitted: int) -> str:
    return f"[skills truncated: {omitted} skill(s) omitted due to instruction budget]"


def limit_skill_instructions(instructions: str, max_chars: int) -> str:
    """按既有解析顺序把 skill 段纳入字符预算。

    字符数按注入正文（含 ``## Skill:`` 标题与段间空行）计算。装不下的段整段跳过，
    不从段中间截断；跳过数量写在末尾一行。若第一段本身就超过预算，只保留该段并
    截断到上限，末尾加 ``[... truncated]``，其余段记入跳过数。
    """
    sections = _SKILL_SECTION_SPLIT.split(instructions) if instructions else []
    if not sections:
        return ""
    if len(sections[0]) > max_chars:
        text = sections[0][:max_chars] + _SECTION_TRUNCATED_MARK
        omitted = len(sections) - 1
        if omitted:
            text = f"{text}\n{_omitted_skills_mark(omitted)}"
        return text

    included: list[str] = []
    used = 0
    omitted = 0
    for section in sections:
        extra = len(section) if not included else 2 + len(section)
        if used + extra <= max_chars:
            included.append(section)
            used += extra
        else:
            omitted += 1
    text = "\n\n".join(included)
    if omitted:
        text = f"{text}\n{_omitted_skills_mark(omitted)}"
    return text


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
