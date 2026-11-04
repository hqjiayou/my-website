"""模型自选 skill 工具（Issue #195，渐进披露 B 层 L2/L3）。

只在 ``AGENT_SKILLS_SELF_SELECT_ENABLED`` 开启时由 toolset_factory 注册；
关闭时本模块不进任何 run，组装与未启用时逐字一致。

- ``load_skill``（L2）：返回正文全文，组装成 ``## Skill: <id>`` 段并经
  ``limit_skill_instructions`` 与预选注入同一字符预算口径。
- ``read_skill_reference``（L3）：只读 discover 已登记的 references 路径，
  清单即准入名单；返回内容设上限截断并留标记。
- 失败一律返回 ``error: ...`` 文本而非抛异常：一次选错 skill 不该拖垮
  整轮 run（与 todo_tools 的平台约定同理）。

遥测：成功 load 的 id 记入 run 级 ``SkillLoadCollector``（模式同
KnowledgeSearchCollector）；run 结束后调用方经 ``merge_skills_applied``
与预选 applied 合并、去重保序，构成最终 skillsApplied。
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass, field
from pathlib import Path

from pydantic_ai.toolsets.function import FunctionToolset

from agent_services.agent.skills import (
    _is_within,
    discover_skills,
    limit_skill_instructions,
)

# reference 单次返回上限：细则/模板的量级在几千字元内，8000 足够覆盖整份
# 模板；再长说明文件本身该拆分，截断并留标记，不静默吞掉后半。
REFERENCE_MAX_CHARS = 8_000
_TRUNCATED_MARK = "[... truncated]"
# 累计预算剩余低于此值时不再截断加载、直接报预算耗尽：截到几十字的
# skill 正文没有可用信息，只会诱导模型按残篇行事。
_MIN_TRUNCATED_LOAD_CHARS = 200


@dataclass
class SkillLoadCollector:
    """本轮经 load_skill 成功加载的 skill id（去重保序）与累计字元数。

    loaded_chars 是自选加载的累计预算消耗：本轮全部 load 共享同一上限，
    不是每次调用各享全额。
    """

    loaded_ids: list[str] = field(default_factory=list)
    loaded_chars: int = 0

    def record(self, skill_id: str) -> None:
        if skill_id not in self.loaded_ids:
            self.loaded_ids.append(skill_id)


def merge_skills_applied(
    preselected: Iterable[str] | None, loaded: Iterable[str] | None
) -> list[str]:
    """预选 applied 在前、自选 loaded 随后，去重保序合并为最终 skillsApplied。"""
    merged: list[str] = []
    for skill_id in list(preselected or []) + list(loaded or []):
        sid = (skill_id or "").strip()
        if sid and sid not in merged:
            merged.append(sid)
    return merged


def build_skill_toolset(
    skills_dir: str,
    *,
    collector: SkillLoadCollector,
    preselected_ids: Iterable[str] = (),
    max_instruction_chars: int | None = None,
) -> FunctionToolset:
    toolset = FunctionToolset()
    preselected = {sid.strip() for sid in preselected_ids if sid and sid.strip()}

    @toolset.tool_plain
    def load_skill(skill_id: str) -> str:
        """Load the full instructions of one skill from the skill catalog.

        Call this when the user's task matches a catalog skill's description
        and that skill is not already active in this run. The returned
        instructions govern how to do the task; follow them. Do not load a
        skill that does not match the task.
        """
        sid = skill_id.strip()
        catalog = discover_skills(skills_dir)
        definition = catalog.get(sid)
        if definition is None:
            available = ", ".join(sorted(catalog)) or "(none)"
            return f"error: unknown skill id '{sid}'; available skill ids: {available}"
        if sid in preselected:
            return (
                f"skill '{sid}' is already active for this run; its instructions "
                "are already in the system prompt, there is no need to load it again."
            )
        if sid in collector.loaded_ids:
            return (
                f"skill '{sid}' is already loaded in this run; follow the "
                "instructions returned by the earlier load_skill call."
            )
        text = f"## Skill: {sid}\n\n{definition.body}"
        if max_instruction_chars is not None:
            # 累计预算：本轮全部自选加载共享同一上限（与预选注入的总量
            # 口径对齐）；逐个 load 各享全额会让总量随加载个数倍增。
            remaining = max_instruction_chars - collector.loaded_chars
            if len(text) > remaining:
                # 首个 load 不设下限（与预选首段截断口径一致）；已有消耗后
                # 剩余太少则直接报耗尽——截到几十字的后续 skill 没有可用
                # 信息，只会诱导模型按残篇行事。
                if collector.loaded_chars > 0 and remaining < _MIN_TRUNCATED_LOAD_CHARS:
                    return (
                        "error: skill instruction budget exhausted for this "
                        f"run; cannot load '{sid}'."
                    )
                text = limit_skill_instructions(text, remaining)
        collector.record(sid)
        collector.loaded_chars += len(text)
        return text

    @toolset.tool_plain
    def read_skill_reference(skill_id: str, reference_path: str) -> str:
        """Read one reference file of a skill.

        ``reference_path`` is the relative path registered for the skill
        (for example ``section-templates.md``). Only registered paths can
        be read; use it when the loaded skill instructions point to a
        reference for details or templates.
        """
        sid = skill_id.strip()
        catalog = discover_skills(skills_dir)
        definition = catalog.get(sid)
        if definition is None:
            available = ", ".join(sorted(catalog)) or "(none)"
            return f"error: unknown skill id '{sid}'; available skill ids: {available}"
        rel = reference_path.strip().replace("\\", "/")
        # 准入只认登记清单：../、绝对路径、未登记路径都在此被拒，
        # 不拿模型给的路径直接拼文件系统。
        if rel not in definition.references:
            registered = ", ".join(definition.references) or "(none)"
            return (
                f"error: reference '{reference_path}' is not registered for "
                f"skill '{sid}'; registered references: {registered}"
            )
        path = Path(skills_dir) / sid / "references" / rel
        # 登记时已验过一次；读时再验，防登记后被换成指向目录外的符号链接。
        if not _is_within(Path(skills_dir), path) or not path.is_file():
            return f"error: reference '{rel}' for skill '{sid}' cannot be read."
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            return f"error: reference '{rel}' for skill '{sid}' cannot be read."
        if len(text) > REFERENCE_MAX_CHARS:
            return text[:REFERENCE_MAX_CHARS] + "\n" + _TRUNCATED_MARK
        return text

    return toolset
