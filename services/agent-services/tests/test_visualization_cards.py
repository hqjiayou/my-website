"""回复内卡片（Issue #179）：chart spec 校验、render_chart 门控、天气卡切分与流式事件。

天气走 httpx MockTransport 离线测（样例 payload 录制自 Open-Meteo 形状），不出网。
"""

import json
from collections.abc import AsyncIterator

import httpx
import pytest
from conftest import make_settings
from pydantic_ai.messages import ModelMessage, ToolReturnPart
from pydantic_ai.models.function import AgentInfo, DeltaToolCall, FunctionModel

from agent_services.agent.chart_tools import RENDER_CHART_TOOL_NAME, build_chart_toolset
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.agent.visualization import (
    MAX_CHART_POINTS_PER_SERIES,
    MAX_CHART_SERIES,
    VisualizationCollector,
    build_chart_card,
)
from agent_services.agent.weather_tools import (
    WEATHER_TOOL_NAME,
    build_weather_card,
    build_weather_toolset,
)
from agent_services.schemas.chat import ChatRequest
from agent_services.schemas.stream import AgentStreamEvent

_LAT = 35.68
_LON = 139.69


def _points(count: int) -> list[dict[str, object]]:
    return [{"label": f"p{i}", "value": i + 1} for i in range(count)]


def _spec(**overrides: object) -> dict[str, object]:
    spec: dict[str, object] = {
        "type": "bar",
        "series": [{"name": "A", "points": _points(3)}],
    }
    spec.update(overrides)
    return spec


# --- chart spec 校验 -------------------------------------------------------


def test_valid_chart_spec_becomes_envelope() -> None:
    card, error = build_chart_card(_spec(title=" Monthly "))
    assert error is None
    assert card is not None
    assert card["kind"] == "chart"
    assert card["title"] == "Monthly"
    payload = card["payload"]
    assert payload["type"] == "bar"  # type: ignore[index]
    assert payload["series"][0]["points"][0] == {"label": "p0", "value": 1}  # type: ignore[index]
    # 无 title 时信封不写 title 键
    card_no_title, _ = build_chart_card(_spec())
    assert card_no_title is not None and "title" not in card_no_title


@pytest.mark.parametrize(
    "spec",
    [
        _spec(type="scatter"),
        _spec(series=[{"name": f"s{i}", "points": _points(1)} for i in range(MAX_CHART_SERIES + 1)]),
        _spec(series=[{"name": "A", "points": _points(MAX_CHART_POINTS_PER_SERIES + 1)}]),
        _spec(series=[{"name": "A", "points": [{"label": "x", "value": float("nan")}]}]),
        _spec(series=[{"name": "A", "points": [{"label": "x", "value": float("inf")}]}]),
        _spec(series=[{"name": "A", "points": [{"label": "x", "value": "abc"}]}]),
        _spec(series=[{"name": "A", "points": [{"label": "x", "value": True}]}]),
        _spec(series=[]),
        _spec(series=[{"name": "A", "points": []}]),
    ],
)
def test_invalid_chart_specs_return_structured_error(spec: dict[str, object]) -> None:
    card, error = build_chart_card(spec)
    assert card is None
    assert error is not None
    assert error["errorCode"] == "INVALID_CHART_SPEC"
    assert error["issues"]


# --- render_chart 工具 ------------------------------------------------------


async def _call_chart(toolset: object, **kwargs: object) -> str:
    tool = toolset.tools[RENDER_CHART_TOOL_NAME]  # type: ignore[attr-defined]
    return await tool.function(**kwargs)


async def test_render_chart_records_card_and_confirms() -> None:
    collector = VisualizationCollector()
    result = await _call_chart(
        build_chart_toolset(collector),
        chart_type="line",
        series=[{"name": "A", "points": [{"label": "Jan", "value": 3}]}],
        title="Trend",
    )
    assert result == "Chart displayed to the user."
    assert len(collector.cards) == 1
    assert collector.cards[0]["kind"] == "chart"
    assert collector.cards[0]["title"] == "Trend"


async def test_render_chart_invalid_spec_returns_error_and_records_nothing() -> None:
    collector = VisualizationCollector()
    result = await _call_chart(
        build_chart_toolset(collector),
        chart_type="bar",
        series=[{"name": "A", "points": [{"label": "x", "value": "abc"}]}],
    )
    parsed = json.loads(result)
    assert parsed["errorCode"] == "INVALID_CHART_SPEC"
    assert collector.cards == []


def _tool_names(toolsets: list[object]) -> set[str]:
    names: set[str] = set()
    for toolset in toolsets:
        tools = getattr(toolset, "tools", None)
        if isinstance(tools, dict):
            names.update(tools.keys())
    return names


def test_render_chart_registered_only_for_preview_with_collector() -> None:
    collector = VisualizationCollector()
    preview = build_execution_toolsets(
        make_settings(),
        "42",
        todo_store=TodoStore(),
        preview=True,
        visualization_collector=collector,
    )
    assert RENDER_CHART_TOOL_NAME in _tool_names(preview)

    formal = build_execution_toolsets(
        make_settings(),
        "42",
        todo_store=TodoStore(),
        preview=False,
        visualization_collector=collector,
    )
    assert RENDER_CHART_TOOL_NAME not in _tool_names(formal)
    assert WEATHER_TOOL_NAME in _tool_names(formal)

    no_collector = build_execution_toolsets(
        make_settings(), "42", todo_store=TodoStore(), preview=True
    )
    assert RENDER_CHART_TOOL_NAME not in _tool_names(no_collector)


def test_chat_request_preview_defaults_false() -> None:
    assert ChatRequest.model_validate({"agentId": "1", "message": "hi"}).preview is False
    assert (
        ChatRequest.model_validate({"agentId": "1", "message": "hi", "preview": True}).preview
        is True
    )


# --- 天气卡 -----------------------------------------------------------------

# 录制的 Open-Meteo 形状样例：hourly 给足两天 48 个条目，验证「未来 24h」窗口。
def _weather_payload() -> dict[str, object]:
    hours = [f"2026-11-02T{h:02d}:00" for h in range(24)] + [
        f"2026-11-03T{h:02d}:00" for h in range(24)
    ]
    return {
        "latitude": 35.6895,
        "longitude": 139.6917,
        "timezone": "Asia/Tokyo",
        "current": {
            "time": "2026-11-02T18:00",
            "temperature_2m": 12.3,
            "apparent_temperature": 10.1,
            "relative_humidity_2m": 55,
            "weather_code": 2,
            "wind_speed_10m": 14.2,
            "precipitation": 0.0,
        },
        "hourly": {
            "time": hours,
            "temperature_2m": [20.0 - i * 0.5 for i in range(48)],
            "precipitation_probability": [i % 100 for i in range(48)],
            "weather_code": [1] * 48,
        },
        "daily": {
            "time": ["2026-11-02", "2026-11-03", "2026-11-04"],
            "temperature_2m_max": [15.0, 16.5, 14.0],
            "temperature_2m_min": [8.0, 9.5, 7.0],
            "precipitation_probability_max": [20, 40, 10],
            "weather_code": [2, 61, 0],
        },
    }


def test_weather_card_slices_current_hourly_daily() -> None:
    card = build_weather_card(_weather_payload(), _LAT, _LON)
    assert card is not None
    assert card["timezone"] == "Asia/Tokyo"
    current = card["current"]
    assert current["temperatureC"] == 12.3  # type: ignore[index]
    assert current["apparentTemperatureC"] == 10.1  # type: ignore[index]
    assert current["humidityPercent"] == 55  # type: ignore[index]
    assert current["windSpeedKmh"] == 14.2  # type: ignore[index]
    assert current["weatherCode"] == 2  # type: ignore[index]
    assert current["weatherText"] == "partly cloudy"  # type: ignore[index]

    hourly = card["hourly"]
    assert len(hourly) == 24  # type: ignore[arg-type]
    assert hourly[0]["time"] == "2026-11-02T18:00"  # type: ignore[index]
    assert hourly[0]["temperatureC"] == 11.0  # type: ignore[index]
    assert hourly[-1]["time"] == "2026-11-03T17:00"  # type: ignore[index]

    daily = card["daily"]
    assert len(daily) == 3  # type: ignore[arg-type]
    assert daily[1]["temperatureMaxC"] == 16.5  # type: ignore[index]
    assert daily[1]["weatherText"] == "slight rain"  # type: ignore[index]
    assert daily[1]["precipitationProbabilityMaxPercent"] == 40  # type: ignore[index]


def test_weather_card_none_for_malformed_payload() -> None:
    assert build_weather_card({"hourly": {}}, _LAT, _LON) is None
    assert build_weather_card({"current": {"time": 5}}, _LAT, _LON) is None


def _weather_client(payload: dict[str, object]) -> httpx.AsyncClient:
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=payload)

    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


async def _call_weather(toolset: object) -> str:
    tool = toolset.tools[WEATHER_TOOL_NAME]  # type: ignore[attr-defined]
    return await tool.function()


async def test_get_weather_emits_card_and_keeps_text_when_collector_given() -> None:
    collector = VisualizationCollector()
    toolset = build_weather_toolset(
        _LAT, _LON, client=_weather_client(_weather_payload()), card_collector=collector
    )
    text = await _call_weather(toolset)
    assert "Now: 12.3°C" in text
    assert len(collector.cards) == 1
    envelope = collector.cards[0]
    assert envelope["kind"] == "weather"
    assert envelope["payload"]["current"]["temperatureC"] == 12.3  # type: ignore[index]


async def test_get_weather_without_collector_emits_no_card_same_text() -> None:
    with_card = build_weather_toolset(
        _LAT,
        _LON,
        client=_weather_client(_weather_payload()),
        card_collector=VisualizationCollector(),
    )
    without_card = build_weather_toolset(_LAT, _LON, client=_weather_client(_weather_payload()))
    assert await _call_weather(with_card) == await _call_weather(without_card)


# --- 流式事件 ----------------------------------------------------------------


def _has_tool_return(messages: list[ModelMessage]) -> bool:
    return any(
        isinstance(part, ToolReturnPart)
        for message in messages
        for part in getattr(message, "parts", [])
    )


async def _collect_stream(model: FunctionModel, **kwargs: object) -> list[AgentStreamEvent]:
    return [
        event
        async for event in stream_chat_events(model, agent_id="1", message="show me", **kwargs)  # type: ignore[arg-type]
    ]


async def test_stream_emits_visualization_after_tool_result_for_chart() -> None:
    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        if _has_tool_return(messages):
            yield "Here is the chart."
            return
        yield {
            0: DeltaToolCall(
                name=RENDER_CHART_TOOL_NAME,
                json_args=json.dumps(
                    {
                        "chart_type": "bar",
                        "series": [{"name": "A", "points": [{"label": "Jan", "value": 3}]}],
                    }
                ),
            )
        }

    collector = VisualizationCollector()
    events = await _collect_stream(
        FunctionModel(stream_function=stream_fn),
        toolsets=[build_chart_toolset(collector)],
        visualization_collector=collector,
    )
    types = [event.type.value for event in events]
    assert types.index("TOOL_RESULT") < types.index("VISUALIZATION") < types.index("DONE")
    card_event = next(event for event in events if event.type.value == "VISUALIZATION")
    assert card_event.data["kind"] == "chart"
    assert card_event.data["payload"]["series"][0]["points"][0]["value"] == 3  # type: ignore[index]
    # 卡片已 drain，不会在 DONE 里重复出现
    assert "visualization" not in json.dumps(events[-1].data).lower()


async def test_stream_emits_weather_card_in_preview_shape() -> None:
    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        if _has_tool_return(messages):
            yield "It is partly cloudy."
            return
        yield {0: DeltaToolCall(name=WEATHER_TOOL_NAME, json_args="{}")}

    collector = VisualizationCollector()
    toolset = build_weather_toolset(
        _LAT, _LON, client=_weather_client(_weather_payload()), card_collector=collector
    )
    events = await _collect_stream(
        FunctionModel(stream_function=stream_fn),
        toolsets=[toolset],
        visualization_collector=collector,
    )
    types = [event.type.value for event in events]
    assert "VISUALIZATION" in types
    card_event = next(event for event in events if event.type.value == "VISUALIZATION")
    assert card_event.data["kind"] == "weather"
    # SSE 线格式：event 名与 data 里的信封原样
    sse = card_event.to_sse()
    assert sse.startswith("event: VISUALIZATION\n")
    assert json.loads(sse.split("data: ", 1)[1])["kind"] == "weather"


async def test_stream_without_collector_emits_no_visualization() -> None:
    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo) -> AsyncIterator[object]:
        if _has_tool_return(messages):
            yield "It is partly cloudy."
            return
        yield {0: DeltaToolCall(name=WEATHER_TOOL_NAME, json_args="{}")}

    toolset = build_weather_toolset(_LAT, _LON, client=_weather_client(_weather_payload()))
    events = await _collect_stream(FunctionModel(stream_function=stream_fn), toolsets=[toolset])
    assert "VISUALIZATION" not in [event.type.value for event in events]
