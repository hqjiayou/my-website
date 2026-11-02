"""实时天气工具：Open-Meteo forecast（免 key），坐标来自本轮请求定位或模型入参。

只做执行层取数：不猜城市、不做 geocoding、不缓存。任何失败只回错误文本，
不让整轮对话失败（与 table_tools 的失败形态一致）。
"""

from __future__ import annotations

import logging
from typing import Any

import httpx
from pydantic_ai.toolsets import FunctionToolset

logger = logging.getLogger(__name__)

WEATHER_TOOL_NAME = "get_weather"
OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
_TIMEOUT_SECONDS = 5.0

NO_LOCATION_TEXT = (
    "Weather lookup needs a location: no coordinates were provided "
    "and this session has no granted location."
)
INVALID_COORDINATES_TEXT = (
    "Weather lookup needs a valid location: latitude and longitude must be given "
    "together, with latitude in [-90, 90] and longitude in [-180, 180]."
)
UNAVAILABLE_TEXT = "Live weather data is temporarily unavailable."

# WMO Weather interpretation codes（Open-Meteo current/hourly/daily 的 weather_code）。
WMO_WEATHER_TEXT: dict[int, str] = {
    0: "clear sky",
    1: "mainly clear",
    2: "partly cloudy",
    3: "overcast",
    45: "fog",
    48: "depositing rime fog",
    51: "light drizzle",
    53: "moderate drizzle",
    55: "dense drizzle",
    56: "light freezing drizzle",
    57: "dense freezing drizzle",
    61: "slight rain",
    63: "moderate rain",
    65: "heavy rain",
    66: "light freezing rain",
    67: "heavy freezing rain",
    71: "slight snow fall",
    73: "moderate snow fall",
    75: "heavy snow fall",
    77: "snow grains",
    80: "slight rain showers",
    81: "moderate rain showers",
    82: "violent rain showers",
    85: "slight snow showers",
    86: "heavy snow showers",
    95: "thunderstorm",
    96: "thunderstorm with slight hail",
    99: "thunderstorm with heavy hail",
}

_FORECAST_PARAMS: dict[str, str] = {
    "current": (
        "temperature_2m,apparent_temperature,relative_humidity_2m,"
        "weather_code,wind_speed_10m,precipitation"
    ),
    "hourly": "temperature_2m,precipitation_probability,weather_code",
    "daily": (
        "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"
    ),
    "forecast_days": "3",
    "timezone": "auto",
}


def _weather_text(code: object) -> str:
    if isinstance(code, bool) or not isinstance(code, (int, float)):
        return "unknown conditions"
    numeric = int(code)
    return WMO_WEATHER_TEXT.get(numeric, f"weather code {numeric}")


def _num(value: object) -> str:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return "n/a"
    return f"{float(value):g}"


def _pick_coordinates(
    latitude: float | None,
    longitude: float | None,
    default_latitude: float | None,
    default_longitude: float | None,
) -> tuple[float, float] | str:
    """定下本轮坐标：入参任一非空就只认入参这一对，否则用会话缺省定位。

    返回 (lat, lon) 或错误文本。校验口径与 prompt_builder._accepted_position 一致：
    必须成对且在范围内，否则不调上游。
    """
    if latitude is not None or longitude is not None:
        candidate = (latitude, longitude)
    else:
        candidate = (default_latitude, default_longitude)
    if candidate[0] is None and candidate[1] is None:
        return NO_LOCATION_TEXT
    if candidate[0] is None or candidate[1] is None:
        return INVALID_COORDINATES_TEXT
    try:
        lat = float(candidate[0])
        lon = float(candidate[1])
    except (TypeError, ValueError):
        return INVALID_COORDINATES_TEXT
    # NaN 的区间比较恒为 False，会落进非法分支，不会被当成合法坐标。
    if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
        return INVALID_COORDINATES_TEXT
    return lat, lon


def _format_summary(payload: dict[str, Any], latitude: float, longitude: float) -> str:
    current = payload.get("current")
    if not isinstance(current, dict) or not isinstance(current.get("time"), str):
        return f"{UNAVAILABLE_TEXT} (unexpected response shape)"
    local_time = current["time"]
    timezone = payload.get("timezone")
    lines = [
        f"Weather for latitude {latitude:.5f}, longitude {longitude:.5f}"
        + (f" (timezone {timezone})" if isinstance(timezone, str) and timezone else "")
        + f", local time {local_time}.",
        "Now: "
        f"{_num(current.get('temperature_2m'))}°C "
        f"(feels like {_num(current.get('apparent_temperature'))}°C), "
        f"{_weather_text(current.get('weather_code'))}, "
        f"humidity {_num(current.get('relative_humidity_2m'))}%, "
        f"wind {_num(current.get('wind_speed_10m'))} km/h, "
        f"precipitation {_num(current.get('precipitation'))} mm.",
    ]
    lines.extend(_hourly_lines(payload.get("hourly"), local_time))
    lines.extend(_daily_lines(payload.get("daily")))
    return "\n".join(lines)


def _hourly_lines(hourly: object, local_time: str) -> list[str]:
    """今日逐小时要点：只列当地今天当前时刻之后（含当前小时）的条目。"""
    if not isinstance(hourly, dict):
        return []
    times = hourly.get("time")
    temperatures = hourly.get("temperature_2m")
    probabilities = hourly.get("precipitation_probability")
    codes = hourly.get("weather_code")
    if not isinstance(times, list) or not isinstance(temperatures, list):
        return []
    today = local_time[:10]
    current_hour = local_time[:13]
    rows: list[str] = []
    for index, moment in enumerate(times):
        if not isinstance(moment, str) or not moment.startswith(today) or moment[:13] < current_hour:
            continue
        temperature = temperatures[index] if index < len(temperatures) else None
        probability = (
            probabilities[index]
            if isinstance(probabilities, list) and index < len(probabilities)
            else None
        )
        code = codes[index] if isinstance(codes, list) and index < len(codes) else None
        rows.append(
            f"- {moment[11:16]}: {_num(temperature)}°C, {_weather_text(code)}, "
            f"precipitation probability {_num(probability)}%."
        )
    if not rows:
        return []
    return ["Rest of today, hour by hour:", *rows]


def _daily_lines(daily: object) -> list[str]:
    if not isinstance(daily, dict):
        return []
    days = daily.get("time")
    highs = daily.get("temperature_2m_max")
    lows = daily.get("temperature_2m_min")
    probabilities = daily.get("precipitation_probability_max")
    codes = daily.get("weather_code")
    if not isinstance(days, list) or not isinstance(highs, list) or not isinstance(lows, list):
        return []
    rows: list[str] = []
    for index, day in enumerate(days):
        if not isinstance(day, str) or index >= len(highs) or index >= len(lows):
            continue
        probability = (
            probabilities[index]
            if isinstance(probabilities, list) and index < len(probabilities)
            else None
        )
        code = codes[index] if isinstance(codes, list) and index < len(codes) else None
        rows.append(
            f"- {day}: high {_num(highs[index])}°C, low {_num(lows[index])}°C, "
            f"{_weather_text(code)}, precipitation probability up to {_num(probability)}%."
        )
    if not rows:
        return []
    return ["Daily forecast:", *rows]


async def _lookup(client: httpx.AsyncClient | None, latitude: float, longitude: float) -> str:
    params: dict[str, str] = {
        "latitude": f"{latitude:g}",
        "longitude": f"{longitude:g}",
        **_FORECAST_PARAMS,
    }
    try:
        if client is not None:
            response = await client.get(OPEN_METEO_FORECAST_URL, params=params)
        else:
            async with httpx.AsyncClient(timeout=_TIMEOUT_SECONDS) as owned:
                response = await owned.get(OPEN_METEO_FORECAST_URL, params=params)
    except httpx.TimeoutException:
        logger.warning("weather lookup timed out")
        return f"{UNAVAILABLE_TEXT} (request timed out)"
    except httpx.HTTPError as exc:
        logger.warning("weather lookup failed", exc_info=exc)
        return f"{UNAVAILABLE_TEXT} (network error)"
    if response.status_code != 200:
        return f"{UNAVAILABLE_TEXT} (upstream returned HTTP {response.status_code})"
    try:
        payload = response.json()
    except ValueError:
        return f"{UNAVAILABLE_TEXT} (unexpected response shape)"
    if not isinstance(payload, dict):
        return f"{UNAVAILABLE_TEXT} (unexpected response shape)"
    return _format_summary(payload, latitude, longitude)


def build_weather_toolset(
    default_latitude: float | None = None,
    default_longitude: float | None = None,
    *,
    client: httpx.AsyncClient | None = None,
) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain(name=WEATHER_TOOL_NAME)
    async def get_weather(
        latitude: float | None = None,
        longitude: float | None = None,
    ) -> str:
        """Get live weather and a 3-day forecast for a location, from Open-Meteo.

        Use this whenever the user asks about current weather or a near-term
        forecast. Omit both coordinates to use the location the user granted
        this session. Pass both coordinates only when the user asks about a
        specific place whose coordinates are known; this tool cannot look up
        coordinates by place name.

        Args:
            latitude: optional latitude in [-90, 90]; must be given together with longitude.
            longitude: optional longitude in [-180, 180]; must be given together with latitude.
        """
        picked = _pick_coordinates(latitude, longitude, default_latitude, default_longitude)
        if isinstance(picked, str):
            return picked
        lat, lon = picked
        return await _lookup(client, lat, lon)

    return toolset
