#!/usr/bin/env python3
"""本地可选：启动 Streamable HTTP MCP echo（平台 smoke，无业务）。

用法（在 agent-services 目录）:
  uv run --extra dev python scripts/run_mcp_echo.py

然后在 .env 中设置:
  MCP_SERVERS_JSON=[{"name":"echo","url":"http://127.0.0.1:3999/mcp"}]
"""

from __future__ import annotations

import os

from agent_services.testing.mcp_echo import build_echo_fastmcp


def main() -> None:
    host = os.environ.get("MCP_ECHO_HOST", "127.0.0.1")
    port = int(os.environ.get("MCP_ECHO_PORT", "3999"))
    mcp = build_echo_fastmcp()
    # FastMCP HTTP transport（Streamable HTTP）
    mcp.run(transport="http", host=host, port=port, path="/mcp")


if __name__ == "__main__":
    main()
