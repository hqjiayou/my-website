"""平台演示用 MCP（无业务语义）：echo / add。

进程内：``build_echo_mcp_toolset``。stdio 子进程：``python -m agent_services.testing.mcp_echo``。
"""

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


def run_echo_stdio() -> None:
    """测试夹具：以 stdio 传输启动 echo/add，供 MCP client 拉起子进程。

    关掉 banner，避免启动时的 PyPI 版本检查拖住握手。
    """
    build_echo_fastmcp().run(transport="stdio", show_banner=False)


if __name__ == "__main__":
    run_echo_stdio()
