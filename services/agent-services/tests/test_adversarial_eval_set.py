"""Issue #191 对抗题集 adversarial_grounding.jsonl 的确定性 validate。

只校验题集结构与覆盖映射（能被 runner 的 load_cases 加载、机制覆盖齐、
来源锚点真实存在），不跑真模型；拦截效果由真模型选跑时按各题
expected_behavior 判读。
"""

import json
from collections import Counter
from pathlib import Path

from agent_services.evals.run_answer_eval import evals_root, load_cases

_EXPECTED_MECHANISMS = {"R3": 3, "R5": 2, "R10": 2, "R2": 2, "R7": 1}


def _raw_cases() -> list[dict[str, object]]:
    path = evals_root() / "adversarial_grounding.jsonl"
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def test_adversarial_set_loads_and_covers_all_mechanisms() -> None:
    path = evals_root() / "adversarial_grounding.jsonl"
    cases = load_cases(path)
    raw = _raw_cases()
    assert len(cases) == len(raw) == sum(_EXPECTED_MECHANISMS.values())
    assert len({case.id for case in cases}) == len(cases)

    mechanisms = Counter(str(item.get("mechanism", "")) for item in raw)
    assert mechanisms == Counter(_EXPECTED_MECHANISMS)
    for item in raw:
        behavior = str(item.get("expected_behavior", ""))
        # 每题必须写明测哪条机制与期望的收口形态，判读不靠猜。
        assert str(item.get("mechanism")) in behavior


def test_adversarial_expected_sources_exist_in_corpus() -> None:
    corpus = json.loads((evals_root() / "corpus.json").read_text(encoding="utf-8"))
    source_ids = {str(doc["source_id"]) for doc in corpus}
    for case in load_cases(evals_root() / "adversarial_grounding.jsonl"):
        assert set(case.expected_sources) <= source_ids, case.id


def test_adversarial_file_is_registered_in_readme() -> None:
    readme = (evals_root() / "README.md").read_text(encoding="utf-8")
    assert "adversarial_grounding.jsonl" in readme


def test_adversarial_path_is_relative_to_evals_root() -> None:
    assert (Path(evals_root()) / "adversarial_grounding.jsonl").is_file()
