"""平台演示用 in-process MCP（无业务语义）：echo / add。"""

from __future__ import annotations

from fastmcp import FastMCP
from pydantic_ai.mcp import MCPToolset


def build_echo_fastmcp() -> FastMCP:
    mcp = FastMCP("echo")

    @mcp.tool
    def echo(text: str) -> str:
        """Return the input text unchanged (MCP smoke tool)."""
        return text

    @mcp.tool
    def add(a: int, b: int) -> int:
        """Add two integers (MCP smoke tool)."""
        return a + b

    return mcp


def build_echo_mcp_toolset(*, prefix: str = "echo") -> object:
    """供测试注入：进程内 FastMCP，无需网络。"""
    return MCPToolset(build_echo_fastmcp()).prefixed(prefix)
