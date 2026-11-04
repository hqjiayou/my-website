"""#182 / #194 skills 的确定性验收：目录装配、description 结构与 eval 题集一致性。

真模型触发率/完成率不在 CI 内跑（沙箱无 AI_MODEL，与 #180 同口径 BLOCKED）；
本文件只验确定性部分：业务 skill 能被既有机制原样加载/装配、description
带明确不适用边界、evals/skill_qa.jsonl 题集结构完整且与 golden_qa 对齐。
"""

from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path

from agent_services.agent.skills import (
    clear_skills_cache,
    default_skills_directory,
    discover_skills,
    resolve_skills,
)
from agent_services.evals.run_answer_eval import evals_root, load_cases

NEW_SKILL_IDS = (
    "cross-doc-aggregation",
    "doc-qa-cited",
    "table-analysis",
    "summarize-transform",
    "doc-coauthoring",
)

# skill 正文里允许以反引号引用的工具名（均为平台真实工具）；
# source_id 不是工具，是枚举/检索结果与 query_site_table 共用的文档标识参数。
KNOWN_TOOLS = {
    "source_id",
    "list_knowledge_documents",
    "search_site_knowledge",
    "query_site_table",
    "render_chart",
    "add_todo",
    "list_todos",
    "update_todo_status",
    "complete_todo",
    "delegate_task",
}

_SNAKE_TOKEN = re.compile(r"`([a-z][a-z0-9]*(?:_[a-z0-9]+)+)`")


def _load_skill_cases() -> list[dict[str, object]]:
    path = evals_root() / "skill_qa.jsonl"
    cases: list[dict[str, object]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            cases.append(json.loads(line))
    return cases


def _catalog():
    clear_skills_cache()
    return discover_skills(default_skills_directory())


def test_new_skills_are_discovered_alongside_existing() -> None:
    catalog = _catalog()
    for skill_id in (*NEW_SKILL_IDS, "orchestration", "platform-smoke"):
        assert skill_id in catalog, f"skill {skill_id} missing from catalog"
    for skill_id in NEW_SKILL_IDS:
        skill = catalog[skill_id]
        assert skill.body.strip(), f"{skill_id} has empty body"
        assert skill.description.strip(), f"{skill_id} has empty description"
        assert len(skill.description) <= 1024


def test_new_skill_descriptions_state_trigger_and_non_trigger() -> None:
    catalog = _catalog()
    for skill_id in NEW_SKILL_IDS:
        description = catalog[skill_id].description
        # 触发口径结构校验：写清适用（Use）与不适用（Do not use）边界。
        assert "Use" in description, f"{skill_id} description lacks a use scope"
        assert "Do not use" in description, f"{skill_id} description lacks a non-use scope"


def test_new_skills_resolve_into_instructions() -> None:
    clear_skills_cache()
    for skill_id in NEW_SKILL_IDS:
        resolution = resolve_skills(
            skills_dir=default_skills_directory(),
            requested_skill_ids=[skill_id],
            default_skill_ids=None,
        )
        assert resolution.applied_ids == (skill_id,)
        assert resolution.missing_ids == ()
        assert resolution.instructions is not None
        assert f"## Skill: {skill_id}" in resolution.instructions


def test_skill_bodies_reference_only_real_tools() -> None:
    catalog = _catalog()
    for skill_id in NEW_SKILL_IDS:
        referenced = set(_SNAKE_TOKEN.findall(catalog[skill_id].body))
        unknown = referenced - KNOWN_TOOLS
        assert not unknown, f"{skill_id} references unknown tools: {sorted(unknown)}"


def test_cross_doc_aggregation_wires_todo_and_enumeration() -> None:
    body = _catalog()["cross-doc-aggregation"].body
    assert "todo" in body.lower()
    assert "`list_knowledge_documents`" in body
    assert "`query_site_table`" in body
    # #183 预算瘦身口径：todo 至多 3 项、逐份抽取并行批次，不逐项标 in-progress。
    assert "at most 3 items" in body
    assert "in one round of parallel calls" in body
    assert "keep exactly one item in progress" not in body


def test_skill_eval_set_structure() -> None:
    cases = _load_skill_cases()
    ids = [case["id"] for case in cases]
    assert len(ids) == len(set(ids)), "duplicate case ids in skill_qa.jsonl"
    by_skill = Counter(case["skill"] for case in cases)
    assert set(by_skill) == set(NEW_SKILL_IDS)
    for skill_id in NEW_SKILL_IDS:
        assert 3 <= by_skill[skill_id] <= 5, f"{skill_id} must carry 3-5 cases"
        kinds = {case["kind"] for case in cases if case["skill"] == skill_id}
        assert kinds == {"positive", "negative", "quality"}, (
            f"{skill_id} must cover positive/negative/quality, got {sorted(kinds)}"
        )
    for case in cases:
        assert isinstance(case["question"], str) and case["question"].strip()
        assert isinstance(case["expected_behavior"], str) and case["expected_behavior"].strip()


def test_skill_eval_set_aligns_with_golden_qa() -> None:
    golden = {case.id: case for case in load_cases()}
    referenced = 0
    for case in _load_skill_cases():
        ref = case.get("golden_ref")
        if not ref:
            continue
        referenced += 1
        assert ref in golden, f"{case['id']} references unknown golden case {ref}"
    assert referenced >= 6, "skill eval set should lean on the #180 golden set"
    # 口径对齐：集合级正例/质量题的期望数字与 golden 集合级家族一致。
    skill_by_id = {case["id"]: case for case in _load_skill_cases()}
    for skill_case_id, golden_id, figure in (
        ("agg-pos-lowest-net", "coll-payslip-lowest-net", "159,366"),
        ("agg-quality-lowest-gross", "coll-payslip-lowest-gross", "195,000"),
    ):
        assert figure in str(skill_by_id[skill_case_id]["expected_answer"])
        assert figure in golden[golden_id].expected_answer


def test_skill_qa_file_lives_next_to_golden_qa() -> None:
    root = Path(evals_root())
    assert (root / "skill_qa.jsonl").is_file()
    assert (root / "golden_qa.jsonl").is_file()
