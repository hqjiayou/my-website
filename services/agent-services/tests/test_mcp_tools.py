"""§19 MCP 平台：配置解析、health、in-process 连通、连接失败诚实失败。"""

from __future__ import annotations

import json
import sys
import time

import anyio
import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.mcp import MCPToolset
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel
from pydantic_ai.toolsets import FunctionToolset

from agent_services.agent.mcp_tools import (
    McpReadyServer,
    build_mcp_client,
    build_mcp_toolsets,
    has_mcp_toolsets,
    mcp_config_snapshot,
    parse_mcp_server_configs,
    probe_mcp_ready,
)
from agent_services.agent.runtime import (
    ChatBlocked,
    ChatReply,
    _TimeoutToolset,
    run_chat,
)
from agent_services.app import create_app
from agent_services.testing.mcp_echo import build_echo_mcp_toolset


def test_build_mcp_toolsets_empty_when_unconfigured() -> None:
    assert build_mcp_toolsets(make_settings()) == []
    assert mcp_config_snapshot(make_settings()).status == "DISABLED"


def test_build_mcp_toolsets_parses_json_entries() -> None:
    payload = json.dumps([{"name": "demo", "url": "http://127.0.0.1:3999/mcp"}])
    toolsets = build_mcp_toolsets(make_settings(mcp_servers_json=payload))
    assert len(toolsets) == 1
    assert has_mcp_toolsets(toolsets) is True
    snap = mcp_config_snapshot(make_settings(mcp_servers_json=payload))
    assert snap.status == "CONFIGURED"
    assert snap.servers[0].name == "demo"


def test_has_mcp_toolsets_sees_through_nested_wrappers() -> None:
    payload = json.dumps([{"name": "demo", "url": "http://127.0.0.1:3999/mcp"}])
    prefixed = build_mcp_toolsets(make_settings(mcp_servers_json=payload))[0]
    nested = _TimeoutToolset(wrapped=prefixed, timeout_seconds=1.0)
    assert has_mcp_toolsets([nested]) is True
    assert has_mcp_toolsets([_TimeoutToolset(wrapped=FunctionToolset(), timeout_seconds=1.0)]) is False


def test_unsupported_transport_is_skipped() -> None:
    payload = json.dumps(
        [
            {"name": "ws", "url": "http://127.0.0.1:1/mcp", "transport": "websocket"},
        ]
    )
    assert parse_mcp_server_configs(payload) == []
    assert mcp_config_snapshot(make_settings(mcp_servers_json=payload)).status == "DISABLED"
    assert build_mcp_toolsets(make_settings(mcp_servers_json=payload)) == []


def test_transport_http_and_sse_resolution() -> None:
    payload = json.dumps(
        [
            {"name": "plain", "url": "http://127.0.0.1:3999/mcp"},
            {"name": "inferred", "url": "http://127.0.0.1:3999/sse"},
            {"name": "forced", "url": "http://127.0.0.1:3999/sse", "transport": "http"},
            {"name": "sse", "url": "http://127.0.0.1:3999/events", "transport": "sse"},
        ]
    )
    configs = parse_mcp_server_configs(payload)
    assert [item.transport for item in configs] == ["http", "sse", "http", "sse"]
    assert len(build_mcp_toolsets(make_settings(mcp_servers_json=payload))) == 4


def _tool_result_text(result: object) -> str:
    data = getattr(result, "data", None)
    if data is not None:
        return str(data)
    content = getattr(result, "content", None) or []
    parts: list[str] = []
    for block in content:
        text = getattr(block, "text", None)
        if isinstance(text, str):
            parts.append(text)
    if parts:
        return "".join(parts)
    return str(result)


def test_stdio_entry_skips_missing_command_and_bad_args(caplog: pytest.LogCaptureFixture) -> None:
    payload = json.dumps(
        [
            {"name": "nocmd", "transport": "stdio"},
            {"name": "blank", "transport": "stdio", "command": " "},
            {"name": "badargs", "transport": "stdio", "command": "python", "args": "nope"},
            {"name": "baditem", "transport": "stdio", "command": "python", "args": ["ok", 1]},
            {"name": "badenv", "transport": "stdio", "command": "python", "env": {"A": 1}},
            {
                "name": "ok",
                "transport": "stdio",
                "command": " python ",
                "args": ["-m", "agent_services.testing.mcp_echo"],
                "env": {"FOO": "bar"},
                "cwd": "/tmp",
                "toolPrefix": "local",
            },
            {"name": "http", "url": "http://127.0.0.1:3999/mcp"},
        ]
    )
    with caplog.at_level("WARNING"):
        configs = parse_mcp_server_configs(payload)
    assert [item.name for item in configs] == ["ok", "http"]
    stdio = configs[0]
    assert stdio.transport == "stdio"
    assert stdio.command == "python"
    assert stdio.args == ("-m", "agent_services.testing.mcp_echo")
    assert stdio.env == {"FOO": "bar"}
    assert stdio.cwd == "/tmp"
    assert stdio.tool_prefix == "local"
    assert stdio.url == ""
    assert configs[1].transport == "http"
    assert any("missing command" in record.message for record in caplog.records)
    assert any("args must be an array of strings" in record.message for record in caplog.records)
    assert any("env must be string-to-string" in record.message for record in caplog.records)


def test_sse_transport_logs_deprecation_warning(caplog: pytest.LogCaptureFixture) -> None:
    payload = json.dumps(
        [
            {"name": "plain", "url": "http://127.0.0.1:3999/mcp", "transport": "http"},
            {"name": "legacy", "url": "http://127.0.0.1:3999/events", "transport": "sse"},
        ]
    )
    with caplog.at_level("WARNING"):
        configs = parse_mcp_server_configs(payload)
    assert [item.transport for item in configs] == ["http", "sse"]
    assert configs[1].url == "http://127.0.0.1:3999/events"
    sse_warnings = [
        record.message
        for record in caplog.records
        if record.levelname == "WARNING" and "deprecated" in record.message
    ]
    assert len(sse_warnings) == 1
    assert "legacy" in sse_warnings[0]
    assert "plain" not in sse_warnings[0]


async def test_stdio_echo_server_lists_and_calls_tools(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("agent_services.agent.mcp_tools.READY_PROBE_TOTAL_TIMEOUT_SECONDS", 30.0)
    payload = json.dumps(
        [
            {
                "name": "echo",
                "transport": "stdio",
                "command": sys.executable,
                "args": ["-m", "agent_services.testing.mcp_echo"],
                "initTimeoutSeconds": 20,
            }
        ]
    )
    configs = parse_mcp_server_configs(payload)
    assert len(configs) == 1
    assert len(build_mcp_toolsets(make_settings(mcp_servers_json=payload))) == 1

    report = await probe_mcp_ready(make_settings(mcp_servers_json=payload))
    assert report.ok is True
    assert report.servers[0].name == "echo"
    assert (report.servers[0].tool_count or 0) >= 2

    async with build_mcp_client(configs[0]) as client:
        tools = await client.list_tools()
        names = {tool.name for tool in tools}
        assert {"echo", "add"} <= names
        echoed = await client.call_tool("echo", arguments={"text": "ping"})
        added = await client.call_tool("add", arguments={"a": 2, "b": 3})
    assert "ping" in _tool_result_text(echoed)
    assert "5" in _tool_result_text(added)


def test_invalid_mcp_json_is_skipped() -> None:
    assert build_mcp_toolsets(make_settings(mcp_servers_json="{")) == []
    assert mcp_config_snapshot(make_settings(mcp_servers_json="{")).status == "INVALID_CONFIG"


def test_enabled_string_false_is_skipped(caplog) -> None:
    payload = json.dumps(
        [
            {"name": "strfalse", "url": "http://127.0.0.1:3999/mcp", "enabled": "false"},
            {"name": "on", "url": "http://127.0.0.1:3999/mcp"},
        ]
    )
    with caplog.at_level("WARNING"):
        configs = parse_mcp_server_configs(payload)
    assert [item.name for item in configs] == ["on"]
    assert configs[0].enabled is True
    assert len(build_mcp_toolsets(make_settings(mcp_servers_json=payload))) == 1
    assert any("not a JSON boolean" in record.message for record in caplog.records)


def test_enabled_zero_is_skipped(caplog) -> None:
    payload = json.dumps(
        [
            {"name": "zero", "url": "http://127.0.0.1:1/mcp", "enabled": 0},
            {"name": "on", "url": "http://127.0.0.1:3999/mcp"},
        ]
    )
    with caplog.at_level("WARNING"):
        configs = parse_mcp_server_configs(payload)
    assert [item.name for item in configs] == ["on"]
    assert len(build_mcp_toolsets(make_settings(mcp_servers_json=payload))) == 1
    assert any("not a JSON boolean" in record.message for record in caplog.records)


def test_parse_respects_enabled_false_and_headers_and_prefix() -> None:
    payload = json.dumps(
        [
            {"name": "off", "url": "http://127.0.0.1:1/mcp", "enabled": False},
            {
                "name": "on",
                "url": "http://127.0.0.1:3999/mcp",
                "headers": {"Authorization": "Bearer x"},
                "toolPrefix": "demo",
                "initTimeoutSeconds": 1.5,
            },
            {"name": "on", "url": "http://127.0.0.1:3998/mcp"},
        ]
    )
    configs = parse_mcp_server_configs(payload)
    assert len(configs) == 2
    assert configs[0].enabled is False
    assert configs[1].tool_prefix == "demo"
    assert configs[1].headers == {"Authorization": "Bearer x"}
    assert configs[1].init_timeout_seconds == 1.5
    toolsets = build_mcp_toolsets(make_settings(mcp_servers_json=payload))
    assert len(toolsets) == 1


def test_health_reports_mcp_status() -> None:
    from agent_services.agent.model_factory import ModelHandle

    settings = make_settings(
        ai_model="test",
        mcp_servers_json=json.dumps([{"name": "echo", "url": "http://127.0.0.1:3999/mcp"}]),
    )
    app = create_app(
        settings=settings,
        model_handle=ModelHandle(
            model=TestModel(),
            spec="test",
            blocked_reason=None,
        ),
    )
    body = TestClient(app).get("/internal/v1/health", headers=TOKEN_HEADER).json()
    assert body["mcpStatus"] == "CONFIGURED"
    assert body["mcpServers"][0]["name"] == "echo"


def test_mcp_ready_disabled_is_ok() -> None:
    from agent_services.agent.model_factory import ModelHandle

    app = create_app(
        settings=make_settings(ai_model="test"),
        model_handle=ModelHandle(model=TestModel(), spec="test", blocked_reason=None),
    )
    response = TestClient(app).get("/internal/v1/mcp/ready", headers=TOKEN_HEADER)
    assert response.status_code == 200
    assert response.json() == {"ok": True, "mcpStatus": "DISABLED", "servers": []}


def test_mcp_ready_unreachable_is_503_without_url() -> None:
    from agent_services.agent.model_factory import ModelHandle

    settings = make_settings(
        ai_model="test",
        mcp_servers_json=json.dumps(
            [{"name": "dead", "url": "http://127.0.0.1:1/mcp", "initTimeoutSeconds": 0.2}]
        ),
    )
    app = create_app(
        settings=settings,
        model_handle=ModelHandle(model=TestModel(), spec="test", blocked_reason=None),
    )
    response = TestClient(app).get("/internal/v1/mcp/ready", headers=TOKEN_HEADER)
    body = response.json()
    assert response.status_code == 503
    assert body["ok"] is False
    assert body["errorCode"] == "MCP_UNAVAILABLE"
    assert body["servers"][0]["name"] == "dead"
    assert body["servers"][0]["ok"] is False
    assert "127.0.0.1" not in response.text


def test_mcp_ready_invalid_config_is_503() -> None:
    from agent_services.agent.model_factory import ModelHandle

    app = create_app(
        settings=make_settings(ai_model="test", mcp_servers_json="{"),
        model_handle=ModelHandle(model=TestModel(), spec="test", blocked_reason=None),
    )
    response = TestClient(app).get("/internal/v1/mcp/ready", headers=TOKEN_HEADER)
    assert response.status_code == 503
    assert response.json()["errorCode"] == "MCP_UNAVAILABLE"
    assert response.json()["mcpStatus"] == "INVALID_CONFIG"


def test_health_reports_disabled_without_mcp() -> None:
    from agent_services.agent.model_factory import ModelHandle

    app = create_app(
        settings=make_settings(ai_model="test"),
        model_handle=ModelHandle(model=TestModel(), spec="test", blocked_reason=None),
    )
    body = TestClient(app).get("/internal/v1/health", headers=TOKEN_HEADER).json()
    assert body["mcpStatus"] == "DISABLED"
    assert body["mcpServers"] == []


async def test_inprocess_mcp_echo_tool_callable() -> None:
    toolset = build_echo_mcp_toolset(prefix="echo")
    round_n = {"n": 0}

    def model_fn(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        round_n["n"] += 1
        names = [t.name for t in info.function_tools]
        if round_n["n"] == 1:
            assert "echo_echo" in names
            assert "echo_add" in names
            return ModelResponse(
                parts=[ToolCallPart(tool_name="echo_echo", args={"text": "ping"})]
            )
        return ModelResponse(parts=[TextPart(content="mcp-ok")])

    outcome = await run_chat(
        FunctionModel(model_fn),
        agent_id="1",
        message="use mcp echo",
        toolsets=[toolset],
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "mcp-ok"


async def test_mcp_connect_failure_is_mcp_unavailable() -> None:
    toolset = MCPToolset(
        "http://127.0.0.1:1/mcp",
        init_timeout=0.5,
    ).prefixed("dead")
    outcome = await run_chat(
        TestModel(),
        agent_id="1",
        message="hello",
        toolsets=[toolset],
    )
    assert isinstance(outcome, ChatBlocked)
    assert outcome.error_code == "MCP_UNAVAILABLE"
    assert "MCP unavailable" in outcome.message
    assert "127.0.0.1" not in outcome.message


async def test_mcp_stream_connect_failure_emits_mcp_unavailable() -> None:
    from agent_services.agent.stream_runtime import stream_chat_events
    from agent_services.schemas.stream import AgentStreamEventType

    toolset = MCPToolset(
        "http://127.0.0.1:1/mcp",
        init_timeout=0.5,
    ).prefixed("dead")
    events = [
        event
        async for event in stream_chat_events(
            TestModel(),
            agent_id="1",
            message="hello",
            toolsets=[toolset],
        )
    ]
    assert events[0].type == AgentStreamEventType.START
    error = next(e for e in events if e.type == AgentStreamEventType.ERROR)
    assert error.data["errorCode"] == "MCP_UNAVAILABLE"
    assert "127.0.0.1" not in error.data["message"]


async def test_mcp_tool_call_emits_tool_sse_events() -> None:
    """设计 §19.2/§19.11：MCP 工具名（含 prefix）须出现在 TOOL_* SSE。"""
    from pydantic_ai.models.function import DeltaToolCall

    from agent_services.agent.stream_runtime import stream_chat_events
    from agent_services.schemas.stream import AgentStreamEventType

    toolset = build_echo_mcp_toolset(prefix="echo")

    async def stream_fn(messages: list[ModelMessage], _info: AgentInfo):
        has_tool_result = any("ping" in repr(m) and "echo" in repr(m).lower() for m in messages)
        if not has_tool_result:
            yield {0: DeltaToolCall(name="echo_echo", json_args='{"text":"ping"}')}
            return
        yield "mcp-stream-ok"

    events = [
        event
        async for event in stream_chat_events(
            FunctionModel(stream_function=stream_fn),
            agent_id="1",
            message="use mcp echo",
            toolsets=[toolset],
        )
    ]
    tool_names = [
        e.data.get("toolName")
        for e in events
        if e.type in (AgentStreamEventType.TOOL_START, AgentStreamEventType.TOOL_RESULT)
    ]
    assert "echo_echo" in tool_names
    result = next(e for e in events if e.type == AgentStreamEventType.TOOL_RESULT)
    assert result.data["ok"] is True
    assert "content" not in result.data
    assert any(
        e.type == AgentStreamEventType.DONE and e.data.get("reply") == "mcp-stream-ok"
        for e in events
    )


async def test_mcp_ready_probes_run_in_parallel(monkeypatch: pytest.MonkeyPatch) -> None:
    delay = 0.3
    inflight = 0
    peak = 0

    async def fake_probe(config):
        nonlocal inflight, peak
        inflight += 1
        peak = max(peak, inflight)
        await anyio.sleep(delay)
        inflight -= 1
        return McpReadyServer(name=config.name, ok=True, tool_count=1)

    monkeypatch.setattr("agent_services.agent.mcp_tools._probe_one", fake_probe)
    payload = json.dumps(
        [
            {"name": "slow-a", "url": "http://127.0.0.1:9/mcp"},
            {"name": "slow-b", "url": "http://127.0.0.1:9/mcp"},
        ]
    )
    started = time.perf_counter()
    report = await probe_mcp_ready(make_settings(mcp_servers_json=payload))
    elapsed = time.perf_counter() - started

    assert peak == 2
    # 串行至少 2 * delay；并行应接近单次 delay，留出调度余量
    assert elapsed < delay * 2 * 0.75
    assert report.ok is True
    assert [item.name for item in report.servers] == ["slow-a", "slow-b"]


async def test_mcp_ready_total_timeout_marks_unfinished_server(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fake_probe(config):
        if config.name == "slow":
            await anyio.sleep(2)
        return McpReadyServer(name=config.name, ok=True, tool_count=1)

    monkeypatch.setattr("agent_services.agent.mcp_tools._probe_one", fake_probe)
    monkeypatch.setattr("agent_services.agent.mcp_tools.READY_PROBE_TOTAL_TIMEOUT_SECONDS", 0.2)
    payload = json.dumps(
        [
            {"name": "fast", "url": "http://127.0.0.1:9/mcp"},
            {"name": "slow", "url": "http://127.0.0.1:9/mcp"},
        ]
    )
    report = await probe_mcp_ready(make_settings(mcp_servers_json=payload))
    assert report.ok is False
    assert report.status == "CONFIGURED"
    assert [item.name for item in report.servers] == ["fast", "slow"]
    assert report.servers[0].ok is True
    assert report.servers[1].ok is False
    assert report.servers[1].error == "probe timed out"
    assert report.message is not None and "slow" in report.message


def test_health_disabled_when_all_entries_disabled() -> None:
    payload = json.dumps([{"name": "off", "url": "http://127.0.0.1:1/mcp", "enabled": False}])
    snap = mcp_config_snapshot(make_settings(mcp_servers_json=payload))
    assert snap.status == "DISABLED"
    assert [s.name for s in snap.servers] == ["off"]
