"""会话上下文时间段。断言对象是组装出的 system prompt，时钟固定。"""

from datetime import datetime, timezone

from agent_services.agent.harness import assemble_harness
from agent_services.agent.prompt_builder import build_prompt_parts
from agent_services.schemas.chat import ChatRequest, PromptProfile

# 2026-10-31 10:30 UTC：东京仍是 31 日，圣诞岛线以东已是 11 月 1 日，帕果帕果还在 10 月 30 日。
_CROSS_DATE = datetime(2026, 10, 31, 10, 30, tzinfo=timezone.utc)
# 2026-03-08 是美国东部夏令时切换日。07:30 UTC 已过 02:00，当地是 EDT 03:30，不是 EST 02:30。
_DST = datetime(2026, 3, 8, 7, 30, tzinfo=timezone.utc)


def _system(zone: str | None, now: datetime, **location: float) -> str:
    system, user = build_prompt_parts(
        "1",
        "hi",
        PromptProfile(systemPrompt="SITE-PROMPT-ONLY"),
        locale="zh-CN",
        timezone_name=zone,
        now=now,
        **location,
    )
    assert user == "hi"
    assert system.startswith("SITE-PROMPT-ONLY")
    assert system.index("# Session context") < system.index("Reply in this locale: zh-CN.")
    assert system.endswith("Reply in this locale: zh-CN.")
    return system


def test_chat_request_accepts_missing_and_present_timezone() -> None:
    missing = ChatRequest.model_validate({"agentId": "1", "message": "hi"})
    assert missing.timezone is None
    present = ChatRequest.model_validate(
        {"agentId": "1", "message": "hi", "locale": "zh-CN", "timezone": "Asia/Tokyo"}
    )
    assert present.timezone == "Asia/Tokyo"
    assert present.locale == "zh-CN"
    located = ChatRequest.model_validate(
        {
            "agentId": "1",
            "message": "hi",
            "latitude": 35.6,
            "longitude": 139.7,
            "accuracyMeters": 12,
        }
    )
    assert located.latitude == 35.6
    assert located.longitude == 139.7
    assert located.accuracy_meters == 12


def test_same_instant_renders_different_dates_across_zones() -> None:
    tokyo = _system("Asia/Tokyo", _CROSS_DATE)
    kiritimati = _system("Pacific/Kiritimati", _CROSS_DATE)
    pago = _system("Pacific/Pago_Pago", _CROSS_DATE)

    assert "Local date: 2026-10-31" in tokyo
    assert "Weekday: Saturday" in tokyo
    assert "Local time: 19:30" in tokyo
    assert "UTC+09:00" in tokyo
    assert "Timezone: Asia/Tokyo" in tokyo
    assert "answer from Local time" in tokyo
    assert "Do not convert Local time to UTC." in tokyo
    assert "Location permission was not granted" in tokyo
    assert "Do not invent a city from Timezone." in tokyo
    assert "Latitude:" not in tokyo
    assert "Timezone fallback" not in tokyo

    assert "Local date: 2026-11-01" in kiritimati
    assert "Weekday: Sunday" in kiritimati
    assert "Local time: 00:30" in kiritimati
    assert "Timezone: Pacific/Kiritimati" in kiritimati

    assert "Local date: 2026-10-30" in pago
    assert "Weekday: Friday" in pago
    assert "Local time: 23:30" in pago
    assert "Timezone: Pacific/Pago_Pago" in pago

    dates = {
        line
        for text in (tokyo, kiritimati, pago)
        for line in text.splitlines()
        if line.startswith("Local date:")
    }
    assert dates == {
        "Local date: 2026-10-31",
        "Local date: 2026-11-01",
        "Local date: 2026-10-30",
    }


def test_new_york_dst_switch_day_uses_daylight_offset() -> None:
    system = _system("America/New_York", _DST)
    assert "Local date: 2026-03-08" in system
    assert "Weekday: Sunday" in system
    assert "Local time: 03:30" in system
    assert "Timezone: America/New_York" in system
    assert "Timezone fallback" not in system


def test_missing_or_invalid_timezone_falls_back_to_utc() -> None:
    for zone in (None, "", "   ", "Not/AZone", "UTC+9"):
        system = _system(zone, _CROSS_DATE)
        assert "Local date: 2026-10-31" in system
        assert "Local time: 10:30" in system
        assert "Timezone: UTC" in system
        assert "UTC+00:00" in system
        assert "Location permission was not granted" in system
        assert "Timezone fallback: UTC" in system
        assert "Latitude:" not in system
        assert "Not/AZone" not in system
        assert "UTC+9" not in system


def test_assembled_harness_prompt_contains_session_context() -> None:
    harness = assemble_harness(
        agent_id="1",
        message="hi",
        prompt_profile=PromptProfile(systemPrompt="SITE-PROMPT-ONLY"),
        timezone_name="Asia/Tokyo",
        now=_CROSS_DATE,
        harness_dir="tests",
    )
    assert "SITE-PROMPT-ONLY" in harness.system_prompt
    assert "Local date: 2026-10-31" in harness.system_prompt
    assert "Local time: 19:30" in harness.system_prompt
    assert "Timezone: Asia/Tokyo" in harness.system_prompt
    assert "Location permission was not granted" in harness.system_prompt
    assert "Timezone fallback" not in harness.system_prompt


def test_geolocation_permission_is_the_position_and_timezone_stays_the_clock() -> None:
    system = _system(
        "Asia/Tokyo",
        _CROSS_DATE,
        latitude=35.681236,
        longitude=139.767125,
        accuracy_meters=18,
    )
    assert "Latitude: 35.68124" in system
    assert "Longitude: 139.76712" in system
    assert "Accuracy meters: 18" in system
    assert "Geolocation API" in system
    assert "Do not ask which city." in system
    assert "Do not treat Timezone as the city." in system
    assert "Location permission was not granted" not in system

    missing_one = _system("Asia/Tokyo", _CROSS_DATE, latitude=35.6)
    assert "Latitude:" not in missing_one
    assert "Location permission was not granted" in missing_one

    out_of_range = _system("Asia/Tokyo", _CROSS_DATE, latitude=95, longitude=139)
    assert "Latitude:" not in out_of_range
