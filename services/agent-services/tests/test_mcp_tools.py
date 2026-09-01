"""Phase 5 MCP 配置解析测试。"""

import json

from agent_services.agent.mcp_tools import build_mcp_toolsets, has_mcp_toolsets
from conftest import make_settings


def test_build_mcp_toolsets_empty_when_unconfigured() -> None:
    assert build_mcp_toolsets(make_settings()) == []


def test_build_mcp_toolsets_parses_json_entries() -> None:
    payload = json.dumps([{"name": "demo", "url": "http://127.0.0.1:3999/mcp"}])
    toolsets = build_mcp_toolsets(make_settings(mcp_servers_json=payload))
    assert len(toolsets) == 1
    assert has_mcp_toolsets(toolsets) is True


def test_invalid_mcp_json_is_skipped() -> None:
    assert build_mcp_toolsets(make_settings(mcp_servers_json="{")) == []
