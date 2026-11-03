"""POST /internal/v1/experience/extract 与提炼逻辑测试（Issue #184 UoW-2）。

覆盖：结构化输出解析与 turn 映射、空结果不是错误、与已有标题重复的候选被丢、
输入提示带已有标题、模型缺失 BLOCKED、模型失败诚实回错、提炼 prompt 的边界字样。
"""

import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.exceptions import ModelAPIError
from pydantic_ai.messages import ModelMessage, ModelResponse, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel

from agent_services.agent.experience import (
    _SYSTEM_PROMPT,
    ExperienceExtractionUnavailable,
    extract_experience_lessons,
)
from agent_services.agent.model_factory import ModelHandle
from agent_services.app import create_app
from agent_services.schemas.experience import ExperienceTurn

_TURNS = [
    ExperienceTurn(
        turnId="101",
        userMessage="查一下上个月的工资单合计",
        assistantReply="工具调用失败后给出了错误合计",
        metaSummary="signals: TOOL_FAILURE; failed tools: query_table(ok=false)",
    ),
    ExperienceTurn(
        turnId="102",
        userMessage="以后合计按实发口径",
        assistantReply="好的，按实发口径",
        metaSummary="signals: (none)",
    ),
]


def _output_argument(info: AgentInfo) -> tuple[str, str]:
    tool = info.output_tools[0]
    schema = tool.parameters_json_schema
    properties = schema.get("properties") or {}
    for name, spec in properties.items():
        if isinstance(spec, dict) and spec.get("type") == "array":
            return tool.name, name
    raise AssertionError(f"extract tool has no array argument: {schema}")


def _model(candidates: list[dict] | None, seen: list[str] | None = None, *, boom: bool = False):
    def respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        if seen is not None:
            for message in messages:
                for part in getattr(message, "parts", []):
                    content = getattr(part, "content", None)
                    if isinstance(content, str):
                        seen.append(content)
        if boom:
            raise ModelAPIError("fake", "provider down")
        tool_name, arg_name = _output_argument(info)
        payload = [] if candidates is None else candidates
        return ModelResponse(
            parts=[ToolCallPart(tool_name=tool_name, args={arg_name: payload}, tool_call_id="extract")]
        )

    return FunctionModel(respond)


def _candidate(turn_index: int, title: str, lesson: str = "Do the thing differently.") -> dict:
    return {
        "turn_index": turn_index,
        "title": title,
        "symptom": "查询工具失败后仍给出合计",
        "lesson": lesson,
        "evidence": f"Turn {turn_index} carries a tool failure signal",
    }


async def test_extract_returns_structured_candidates_mapped_to_turns() -> None:
    model = _model([_candidate(1, "工具失败时不得编造结果"), _candidate(2, "合计默认实发口径")])
    extraction = await extract_experience_lessons(model, turns=_TURNS, existing_titles=[])
    assert [item.source_turn_id for item in extraction.candidates] == ["101", "102"]
    first = extraction.candidates[0]
    assert first.title == "工具失败时不得编造结果"
    assert first.lesson == "Do the thing differently."
    assert first.symptom is not None and first.evidence is not None
    assert extraction.usage["requests"] == 1


async def test_extract_drops_out_of_range_and_blank_candidates() -> None:
    model = _model(
        [
            _candidate(9, "不存在的轮次"),
            _candidate(1, "   "),
            _candidate(1, "有效标题", lesson="  "),
            _candidate(2, "有效标题二"),
        ]
    )
    extraction = await extract_experience_lessons(model, turns=_TURNS, existing_titles=[])
    assert [item.title for item in extraction.candidates] == ["有效标题二"]


async def test_extract_empty_result_is_not_an_error() -> None:
    extraction = await extract_experience_lessons(_model([]), turns=_TURNS, existing_titles=[])
    assert extraction.candidates == []


async def test_extract_drops_candidate_duplicating_existing_title() -> None:
    model = _model([_candidate(1, "工具失败时不得编造结果"), _candidate(2, "合计默认实发口径")])
    extraction = await extract_experience_lessons(
        model, turns=_TURNS, existing_titles=["  工具失败时不得编造结果  "]
    )
    assert [item.title for item in extraction.candidates] == ["合计默认实发口径"]


async def test_extract_prompt_carries_existing_titles_for_dedup() -> None:
    seen: list[str] = []
    model = _model([], seen)
    await extract_experience_lessons(model, turns=_TURNS, existing_titles=["既有教训甲"])
    joined = "\n".join(seen)
    assert "既有教训甲" in joined
    assert "Turn 1:" in joined and "Turn 2:" in joined
    assert "TOOL_FAILURE" in joined


async def test_extract_failure_raises_unavailable() -> None:
    with pytest.raises(ExperienceExtractionUnavailable):
        await extract_experience_lessons(_model(None, boom=True), turns=_TURNS, existing_titles=[])


def test_system_prompt_states_three_categories_and_boundaries() -> None:
    assert "Failure cases" in _SYSTEM_PROMPT
    assert "Convention agreements" in _SYSTEM_PROMPT
    assert "Tool-use lessons" in _SYSTEM_PROMPT
    # 禁区：用户事实归长期记忆，不许提炼成经验
    assert "Never extract facts about the user" in _SYSTEM_PROMPT
    assert "long-term memory" in _SYSTEM_PROMPT
    assert "small talk" in _SYSTEM_PROMPT
    # 去重与证据要求写死在 prompt 里
    assert "Existing lesson titles" in _SYSTEM_PROMPT
    assert "evidence" in _SYSTEM_PROMPT
    assert "verbatim into a system prompt" in _SYSTEM_PROMPT


def _client(model) -> TestClient:
    handle = ModelHandle(model=model, spec="test:model", blocked_reason=None)
    return TestClient(create_app(make_settings(), model_handle=handle))


def _body() -> dict:
    return {
        "turns": [
            {
                "turnId": turn.turn_id,
                "userMessage": turn.user_message,
                "assistantReply": turn.assistant_reply,
                "metaSummary": turn.meta_summary,
            }
            for turn in _TURNS
        ],
        "existingTitles": [],
    }


def test_endpoint_blocked_without_model() -> None:
    response = TestClient(create_app(make_settings())).post(
        "/internal/v1/experience/extract", headers=TOKEN_HEADER, json=_body()
    )
    assert response.status_code == 503
    assert response.json()["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"


def test_endpoint_returns_candidates_usage_and_model_spec() -> None:
    response = _client(_model([_candidate(2, "合计默认实发口径")])).post(
        "/internal/v1/experience/extract", headers=TOKEN_HEADER, json=_body()
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["modelSpec"] == "test:model"
    assert len(data["candidates"]) == 1
    candidate = data["candidates"][0]
    assert candidate["sourceTurnId"] == "102"
    assert candidate["title"] == "合计默认实发口径"
    assert data["usage"]["requests"] == 1


def test_endpoint_uses_request_model_entry(monkeypatch: pytest.MonkeyPatch) -> None:
    def build(spec: str, *, api_key: str, base_url: str) -> FunctionModel:
        assert spec == "fake:from-java"
        assert api_key == "secret-key"
        return _model([_candidate(1, "入口链提炼")])

    monkeypatch.setattr("agent_services.agent.model_factory._build_primary", build)
    body = _body()
    body["model"] = {"spec": "fake:from-java", "apiKey": "secret-key"}
    response = TestClient(create_app(make_settings())).post(
        "/internal/v1/experience/extract", headers=TOKEN_HEADER, json=body
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["modelSpec"] == "fake:from-java"
    assert data["candidates"][0]["title"] == "入口链提炼"


def test_endpoint_model_failure_maps_to_blocked() -> None:
    response = _client(_model(None, boom=True)).post(
        "/internal/v1/experience/extract", headers=TOKEN_HEADER, json=_body()
    )
    assert response.status_code == 503
    assert response.json()["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"


def test_endpoint_requires_at_least_one_turn() -> None:
    response = _client(TestModel()).post(
        "/internal/v1/experience/extract", headers=TOKEN_HEADER, json={"turns": []}
    )
    # 应用把请求校验失败统一映射为 400 BAD_REQUEST（见 api/errors 的校验处理）
    assert response.status_code == 400
