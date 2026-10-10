"""§19 MCP 平台：配置解析、health、in-process 连通、连接失败诚实失败。"""

from __future__ import annotations

import json

from pydantic_ai.mcp import MCPToolset
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel
from pydantic_ai.toolsets import FunctionToolset

from agent_services.agent.mcp_tools import (
    build_mcp_toolsets,
    has_mcp_toolsets,
    mcp_config_snapshot,
    parse_mcp_server_configs,
)
from agent_services.agent.runtime import ChatBlocked, ChatReply, _TimeoutToolset, run_chat
from agent_services.app import create_app
from agent_services.testing.mcp_echo import build_echo_mcp_toolset
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient


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
            {"name": "local", "transport": "stdio", "command": "python"},
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


def test_invalid_mcp_json_is_skipped() -> None:
    assert build_mcp_toolsets(make_settings(mcp_servers_json="{")) == []
    assert mcp_config_snapshot(make_settings(mcp_servers_json="{")).status == "INVALID_CONFIG"


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
        site_id="1",
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
        site_id="1",
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
            site_id="1",
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
            site_id="1",
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


def test_health_disabled_when_all_entries_disabled() -> None:
    payload = json.dumps([{"name": "off", "url": "http://127.0.0.1:1/mcp", "enabled": False}])
    snap = mcp_config_snapshot(make_settings(mcp_servers_json=payload))
    assert snap.status == "DISABLED"
    assert [s.name for s in snap.servers] == ["off"]
