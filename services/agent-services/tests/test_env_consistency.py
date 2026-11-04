"""三处配置键集合一致性锁（Issue #189）。

把「改配置须三处同步」的纪律换成测试：config.py 的 Settings 别名、
services/agent-services/.env.example、docs/operations/ENVIRONMENT_VARIABLES.md
§7 表格的变量列，三处键集合必须对齐。已知差异只允许在 _KNOWN_DIFFERENCES 里
逐键登记（缺席来源＋理由）；出现新漂移、或白名单条目失效（登记的缺席已不存在），
测试都会红并指出漂移键与缺失处。
"""

from __future__ import annotations

import re
from pathlib import Path

from pydantic import AliasChoices

from agent_services.config import Settings

_SERVICE_ROOT = Path(__file__).resolve().parents[1]
_REPO_ROOT = Path(__file__).resolve().parents[3]
_ENV_EXAMPLE = _SERVICE_ROOT / ".env.example"
_ENV_DOC = _REPO_ROOT / "docs" / "operations" / "ENVIRONMENT_VARIABLES.md"

_ENV_NAME_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
_EXAMPLE_LINE_RE = re.compile(r"^\s*#?\s*([A-Z][A-Z0-9_]*)\s*=")
_DOC_ROW_RE = re.compile(r"^\|\s*`([A-Z][A-Z0-9_]*)`")
_SOURCES = ("code", "example", "doc")

# 已知差异白名单：键 -> (允许缺席的来源, 理由)。三处对齐断言只放行这里列明的
# 缺席；新漂移要么补齐三处、要么先在此逐键登记理由，不许默默扩大名单外差异。
_KNOWN_DIFFERENCES: dict[str, tuple[frozenset[str], str]] = {
    "GEMINI_API_KEY": (
        frozenset({"doc"}),
        "Settings 以 AliasChoices 读 GEMINI_API_KEY/GOOGLE_API_KEY；文档 §7 只在 "
        "AI_PROVIDER_API_KEY 行的说明里提及，未单列成行",
    ),
    "GOOGLE_API_KEY": (
        frozenset({"doc"}),
        "同 GEMINI_API_KEY（AliasChoices 别名）",
    ),
    "AI_ALLOW_PROCESS_MODEL": (
        frozenset({"example"}),
        ".env.example 未收录；该开关仅本机调试用（Issue #145 F1），代码与文档 §7 均有",
    ),
    "AGENT_OTEL_INCLUDE_CONTENT": (
        frozenset({"code"}),
        "observability/otel.py 直读 os.environ，非 Settings 字段；example 与文档 §7 均有",
    ),
    "OTEL_EXPORTER_OTLP_ENDPOINT": (
        frozenset({"code"}),
        "同 AGENT_OTEL_INCLUDE_CONTENT，otel.py 直读 os.environ",
    ),
    "OTEL_SERVICE_NAME": (
        frozenset({"code"}),
        "同 AGENT_OTEL_INCLUDE_CONTENT，otel.py 直读 os.environ",
    ),
}


def _code_keys() -> set[str]:
    keys: set[str] = set()
    for field_name, field in Settings.model_fields.items():
        alias = field.validation_alias
        if isinstance(alias, str):
            candidates: list[str] = [alias]
        elif isinstance(alias, AliasChoices):
            candidates = [choice for choice in alias.choices if isinstance(choice, str)]
        elif alias is None:
            candidates = [field_name.upper()]
        else:
            candidates = []
        # 只收环境变量形态的名字：AliasChoices 里的字段名回退（如小写
        # deployment_environment）是构造参数名、不是 env 键，不计。
        keys.update(
            candidate for candidate in candidates if _ENV_NAME_RE.match(candidate)
        )
    return keys


def _example_keys(text: str) -> set[str]:
    # .env.example 里可选键以注释行 `# KEY=` 呈现，与未注释行同等计入。
    keys: set[str] = set()
    for line in text.splitlines():
        match = _EXAMPLE_LINE_RE.match(line)
        if match:
            keys.add(match.group(1))
    return keys


def _doc_keys(text: str) -> set[str]:
    # 只取 §7（Python：agent-services）表格的变量列；文档其余章节是 Java / 前端 /
    # compose 的变量，不在本锁范围。
    section = text.split("## 7.", 1)[1].split("\n## ", 1)[0]
    keys: set[str] = set()
    for line in section.splitlines():
        match = _DOC_ROW_RE.match(line)
        if match:
            keys.add(match.group(1))
    return keys


def _drift_problems(
    sources: dict[str, set[str]],
    known: dict[str, tuple[frozenset[str], str]] = _KNOWN_DIFFERENCES,
) -> list[str]:
    problems: list[str] = []
    all_keys = set().union(*sources.values())
    for key in sorted(all_keys):
        present = [name for name in _SOURCES if key in sources[name]]
        absent = [name for name in _SOURCES if key not in sources[name]]
        if not absent:
            continue
        allowed_absent = known.get(key, (frozenset(), ""))[0]
        for name in absent:
            if name not in allowed_absent:
                problems.append(
                    f"{key}: present in {present}, missing from {name} "
                    "(not in _KNOWN_DIFFERENCES)"
                )
    # 白名单失效检查：登记的缺席必须仍然真实存在，否则条目应删除，避免名单腐烂掩盖新漂移。
    for key, (allowed_absent, _reason) in sorted(known.items()):
        for name in allowed_absent:
            if key in sources[name]:
                problems.append(
                    f"{key}: _KNOWN_DIFFERENCES says it is absent from {name}, "
                    "but it is present now; remove or update the whitelist entry"
                )
    return problems


def test_env_keys_aligned_across_code_example_and_doc() -> None:
    sources = {
        "code": _code_keys(),
        "example": _example_keys(_ENV_EXAMPLE.read_text(encoding="utf-8")),
        "doc": _doc_keys(_ENV_DOC.read_text(encoding="utf-8")),
    }
    problems = _drift_problems(sources)
    assert not problems, "env key drift detected:\n" + "\n".join(problems)


def test_drift_check_flags_a_renamed_example_key() -> None:
    # 敏感性自检（临时副本，不改真文件）：example 里改名一个键必须被检出并点名。
    text = _ENV_EXAMPLE.read_text(encoding="utf-8")
    mutated = text.replace("MCP_SERVERS_JSON=", "MCP_SERVERS_JSON_RENAMED=", 1)
    assert mutated != text
    sources = {
        "code": _code_keys(),
        "example": _example_keys(mutated),
        "doc": _doc_keys(_ENV_DOC.read_text(encoding="utf-8")),
    }
    problems = _drift_problems(sources)
    assert any("MCP_SERVERS_JSON" in problem for problem in problems), problems


def test_drift_check_flags_a_renamed_doc_key() -> None:
    # 同上，改文档 §7 表格里的变量名必须被检出并点名。
    text = _ENV_DOC.read_text(encoding="utf-8")
    mutated = text.replace("`MCP_SERVERS_JSON`", "`MCP_SERVERS_JSON_RENAMED`", 1)
    assert mutated != text
    sources = {
        "code": _code_keys(),
        "example": _example_keys(_ENV_EXAMPLE.read_text(encoding="utf-8")),
        "doc": _doc_keys(mutated),
    }
    problems = _drift_problems(sources)
    assert any("MCP_SERVERS_JSON" in problem for problem in problems), problems
