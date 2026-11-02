"""实时天气工具（#164 UoW-1）：Open-Meteo 取数走 httpx MockTransport，不出网。"""

import httpx
from conftest import make_settings

from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.agent.weather_tools import WEATHER_TOOL_NAME, build_weather_toolset

_DEFAULT_LAT = 35.68
_DEFAULT_LON = 139.69


def _fixture_payload(current_code: int = 2) -> dict[str, object]:
    return {
        "latitude": 35.6895,
        "longitude": 139.6917,
        "timezone": "Asia/Tokyo",
        "current": {
            "time": "2026-11-02T18:00",
            "temperature_2m": 12.3,
            "apparent_temperature": 10.1,
            "relative_humidity_2m": 55,
            "weather_code": current_code,
            "wind_speed_10m": 14.2,
            "precipitation": 0.0,
        },
        "hourly": {
            "time": [
                "2026-11-02T17:00",
                "2026-11-02T18:00",
                "2026-11-02T19:00",
                "2026-11-03T00:00",
            ],
            "temperature_2m": [13.0, 12.3, 11.8, 9.0],
            "precipitation_probability": [5, 10, 20, 30],
            "weather_code": [1, 2, 2, 3],
        },
        "daily": {
            "time": ["2026-11-02", "2026-11-03", "2026-11-04"],
            "temperature_2m_max": [15.0, 16.5, 14.0],
            "temperature_2m_min": [8.0, 9.5, 7.0],
            "precipitation_probability_max": [20, 40, 10],
            "weather_code": [2, 61, 0],
        },
    }


def _client_with(handler) -> httpx.AsyncClient:
    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


def _ok_handler(captured: list[httpx.Request], payload: dict[str, object] | None = None):
    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return httpx.Response(200, json=payload if payload is not None else _fixture_payload())

    return handler


async def _call(toolset: object, **kwargs: float) -> str:
    tool = toolset.tools[WEATHER_TOOL_NAME]  # type: ignore[attr-defined]
    return await tool.function(**kwargs)


async def test_summary_contains_current_hourly_and_daily_highlights() -> None:
    captured: list[httpx.Request] = []
    toolset = build_weather_toolset(
        _DEFAULT_LAT, _DEFAULT_LON, client=_client_with(_ok_handler(captured))
    )

    text = await _call(toolset)

    assert len(captured) == 1
    assert captured[0].url.host == "api.open-meteo.com"
    assert "12.3°C" in text
    assert "partly cloudy" in text
    assert "humidity 55%" in text
    # 逐小时只要今天当前时刻之后的：17:00 与次日 00:00 不出现，19:00 出现。
    assert "19:00" in text
    assert "17:00" not in text
    assert "00:00" not in text
    # 逐日含最高/最低温与降水概率。
    assert "high 15°C" in text
    assert "low 8°C" in text
    assert "precipitation probability up to 20%" in text
    assert "slight rain" in text


async def test_argument_coordinates_override_session_defaults() -> None:
    captured: list[httpx.Request] = []
    toolset = build_weather_toolset(1.0, 2.0, client=_client_with(_ok_handler(captured)))

    await _call(toolset, latitude=10.5, longitude=20.5)

    assert len(captured) == 1
    params = captured[0].url.params
    assert params["latitude"] == "10.5"
    assert params["longitude"] == "20.5"


async def test_no_coordinates_anywhere_returns_error_without_upstream_call() -> None:
    captured: list[httpx.Request] = []
    toolset = build_weather_toolset(client=_client_with(_ok_handler(captured)))

    text = await _call(toolset)

    assert "needs a location" in text
    assert captured == []


async def test_out_of_range_or_unpaired_coordinates_return_error_without_upstream_call() -> None:
    captured: list[httpx.Request] = []
    toolset = build_weather_toolset(
        _DEFAULT_LAT, _DEFAULT_LON, client=_client_with(_ok_handler(captured))
    )

    out_of_range = await _call(toolset, latitude=91.0, longitude=0.0)
    unpaired = await _call(toolset, latitude=10.0)

    assert "valid location" in out_of_range
    assert "valid location" in unpaired
    assert captured == []


async def test_upstream_500_and_timeout_return_error_text_without_raising() -> None:
    def failing_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, text="boom")

    def timeout_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    toolset_500 = build_weather_toolset(
        _DEFAULT_LAT, _DEFAULT_LON, client=_client_with(failing_handler)
    )
    toolset_timeout = build_weather_toolset(
        _DEFAULT_LAT, _DEFAULT_LON, client=_client_with(timeout_handler)
    )

    text_500 = await _call(toolset_500)
    text_timeout = await _call(toolset_timeout)

    assert "temporarily unavailable" in text_500
    assert "HTTP 500" in text_500
    assert "temporarily unavailable" in text_timeout
    assert "timed out" in text_timeout


async def test_unknown_wmo_code_falls_back_to_code_label() -> None:
    captured: list[httpx.Request] = []
    toolset = build_weather_toolset(
        _DEFAULT_LAT,
        _DEFAULT_LON,
        client=_client_with(_ok_handler(captured, _fixture_payload(current_code=42))),
    )

    text = await _call(toolset)

    assert "weather code 42" in text


def test_execution_toolsets_register_weather_tool() -> None:
    toolsets = build_execution_toolsets(make_settings(), "42", todo_store=TodoStore())
    names: set[str] = set()
    for toolset in toolsets:
        tools = getattr(toolset, "tools", None)
        if isinstance(tools, dict):
            names.update(tools.keys())
    assert WEATHER_TOOL_NAME in names
