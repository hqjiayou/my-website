"""Phase 5/§19：MCP — 外部工具经 MCPToolset 接入（配置驱动，可选）。

业务真相仍在 Java；MCP 仅作外部/横切工具总线，禁止替代 Java internal Tools。
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any, Literal

import anyio
from fastmcp import Client
from fastmcp.client.transports import SSETransport, StreamableHttpTransport
from pydantic_ai.mcp import MCPToolset

from agent_services.config import Settings

logger = logging.getLogger(__name__)

_NAME_RE = re.compile(r"^[a-zA-Z0-9_-]+$")
_SUPPORTED_TRANSPORTS = frozenset({"http", "sse"})
# 深检默认短超时：不进 /health，避免拖慢探活（§19.6）
MCP_READY_DEFAULT_TIMEOUT_SECONDS = 3.0

McpStatus = Literal["DISABLED", "CONFIGURED", "INVALID_CONFIG"]


@dataclass(frozen=True)
class McpServerConfig:
    name: str
    url: str
    enabled: bool = True
    headers: dict[str, str] | None = None
    tool_prefix: str | None = None
    init_timeout_seconds: float | None = None
    transport: str = "http"


@dataclass(frozen=True)
class McpServerSummary:
    name: str
    enabled: bool


@dataclass(frozen=True)
class McpConfigSnapshot:
    status: McpStatus
    servers: tuple[McpServerSummary, ...] = ()


@dataclass(frozen=True)
class McpReadyServer:
    name: str
    ok: bool
    tool_count: int | None = None
    error: str | None = None


@dataclass(frozen=True)
class McpReadyReport:
    ok: bool
    status: McpStatus
    servers: tuple[McpReadyServer, ...] = ()
    message: str | None = None


def mcp_config_snapshot(settings: Settings) -> McpConfigSnapshot:
    """供 health 使用：只反映配置态，不做 list_tools 探针。"""
    raw = settings.mcp_servers_json
    if not raw.strip():
        return McpConfigSnapshot(status="DISABLED")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return McpConfigSnapshot(status="INVALID_CONFIG")
    if not isinstance(data, list):
        return McpConfigSnapshot(status="INVALID_CONFIG")
    try:
        configs = parse_mcp_server_configs(raw)
    except ValueError:
        return McpConfigSnapshot(status="INVALID_CONFIG")
    if not configs:
        # 合法 `[]`，或条目全部无效被跳过：无可用 MCP 配置面
        return McpConfigSnapshot(status="DISABLED")
    servers = tuple(McpServerSummary(name=c.name, enabled=c.enabled) for c in configs)
    # 全部 enabled:false 时不会注册任何 MCPToolset，对外语义等同未启用（仍列出条目便于排查）
    status: McpStatus = "CONFIGURED" if any(c.enabled for c in configs) else "DISABLED"
    return McpConfigSnapshot(status=status, servers=servers)


def parse_mcp_server_configs(raw: str) -> list[McpServerConfig]:
    """解析 MCP_SERVERS_JSON；非法整体 JSON 抛 ValueError；单条坏项跳过。"""
    if not raw.strip():
        return []
    data = json.loads(raw)
    if not isinstance(data, list):
        raise ValueError("MCP_SERVERS_JSON must be a JSON array")

    configs: list[McpServerConfig] = []
    seen_names: set[str] = set()
    for index, entry in enumerate(data):
        if not isinstance(entry, dict):
            logger.warning("MCP entry #%s is not an object, skipped", index)
            continue
        name = str(entry.get("name") or f"mcp{index + 1}").strip()
        if not _NAME_RE.match(name):
            # 非法 name 可能是 URL 或令牌，不写进日志
            logger.warning("MCP entry name invalid, skipped")
            continue
        if name in seen_names:
            logger.warning("MCP entry name %r duplicated, skipped", name)
            continue

        enabled = entry.get("enabled", True)
        if enabled is False:
            seen_names.add(name)
            configs.append(
                McpServerConfig(
                    name=name,
                    url=str(entry.get("url") or "").strip(),
                    enabled=False,
                )
            )
            continue
        if enabled is not True:
            # 只接受 JSON 布尔。字符串 "false" / "true"、数字 0 等按未启用跳过，缺省仍为启用。
            logger.warning("MCP entry %r enabled is not a JSON boolean, skipped", name)
            continue

        raw_transport = entry.get("transport")
        if raw_transport is not None and str(raw_transport).strip():
            declared = str(raw_transport).strip().lower()
            if declared not in _SUPPORTED_TRANSPORTS:
                logger.warning(
                    "MCP entry %r transport is not supported (http|sse only; stdio is future), skipped",
                    name,
                )
                continue

        url = entry.get("url")
        url_text = str(url).strip() if url else ""
        if not url_text:
            logger.warning("MCP entry %r missing url, skipped", name)
            continue

        transport = _resolve_transport(raw_transport, url_text)
        if transport is None:
            continue

        headers_raw = entry.get("headers")
        headers: dict[str, str] | None = None
        if isinstance(headers_raw, dict):
            headers = {str(k): str(v) for k, v in headers_raw.items()}

        tool_prefix = entry.get("toolPrefix")
        prefix = str(tool_prefix).strip() if tool_prefix else name

        timeout_raw = entry.get("initTimeoutSeconds")
        init_timeout: float | None = None
        if timeout_raw is not None:
            try:
                init_timeout = float(timeout_raw)
            except (TypeError, ValueError):
                logger.warning("MCP entry %r bad initTimeoutSeconds, ignored", name)

        seen_names.add(name)
        configs.append(
            McpServerConfig(
                name=name,
                url=url_text,
                enabled=True,
                headers=headers,
                tool_prefix=prefix,
                init_timeout_seconds=init_timeout,
                transport=transport,
            )
        )
    return configs


def _resolve_transport(raw: object, url: str) -> str | None:
    """http / sse 才注册。未写 transport 时，url 以 /sse 结尾视为 sse，否则 http。"""
    if raw is None or str(raw).strip() == "":
        return "sse" if url.rstrip("/").endswith("/sse") else "http"
    transport = str(raw).strip().lower()
    if transport in _SUPPORTED_TRANSPORTS:
        return transport
    return None


def build_mcp_client(config: McpServerConfig) -> Client[Any]:
    headers = config.headers
    timeout_kwargs: dict[str, Any] = {}
    if config.init_timeout_seconds is not None:
        # 预构建 Client 时不能再把 init_timeout 传给 MCPToolset
        timeout_kwargs["init_timeout"] = config.init_timeout_seconds
        timeout_kwargs["timeout"] = config.init_timeout_seconds
    if config.transport == "sse":
        return Client(SSETransport(config.url, headers=headers), **timeout_kwargs)
    return Client(StreamableHttpTransport(config.url, headers=headers), **timeout_kwargs)


def build_mcp_toolsets(settings: Settings) -> list[Any]:
    """缺失或空配置 → 不注册 MCP；非法 JSON → 告警并跳过（进程可起）。"""
    raw = settings.mcp_servers_json
    if not raw.strip():
        return []
    try:
        configs = parse_mcp_server_configs(raw)
    except (json.JSONDecodeError, ValueError) as exc:
        logger.warning(
            "Invalid MCP_SERVERS_JSON, skipping MCP toolsets: %s",
            type(exc).__name__,
            exc_info=exc,
        )
        return []

    toolsets: list[Any] = []
    for config in configs:
        if not config.enabled:
            continue
        # headers / transport / init_timeout 在 Client 上（预构建 Client 不能再传给 MCPToolset）
        toolset = MCPToolset(build_mcp_client(config))
        prefix = config.tool_prefix or config.name
        toolsets.append(toolset.prefixed(prefix))
    return toolsets


def _probe_timeout(config: McpServerConfig) -> float:
    if config.init_timeout_seconds is not None and config.init_timeout_seconds > 0:
        return config.init_timeout_seconds
    return MCP_READY_DEFAULT_TIMEOUT_SECONDS


async def probe_mcp_ready(settings: Settings) -> McpReadyReport:
    """对每个 enabled server 短超时 list_tools。不写入 /health。"""
    raw = settings.mcp_servers_json
    snapshot = mcp_config_snapshot(settings)
    if snapshot.status == "INVALID_CONFIG":
        return McpReadyReport(
            ok=False,
            status="INVALID_CONFIG",
            message="MCP_SERVERS_JSON is invalid.",
        )
    if snapshot.status == "DISABLED" or not raw.strip():
        return McpReadyReport(ok=True, status="DISABLED")

    try:
        configs = [item for item in parse_mcp_server_configs(raw) if item.enabled]
    except (json.JSONDecodeError, ValueError):
        return McpReadyReport(
            ok=False,
            status="INVALID_CONFIG",
            message="MCP_SERVERS_JSON is invalid.",
        )
    if not configs:
        return McpReadyReport(ok=True, status="DISABLED")

    results: list[McpReadyServer] = []
    for config in configs:
        results.append(await _probe_one(config))
    failed = [item.name for item in results if not item.ok]
    if failed:
        return McpReadyReport(
            ok=False,
            status="CONFIGURED",
            servers=tuple(results),
            message="MCP server unreachable: " + ", ".join(failed),
        )
    return McpReadyReport(ok=True, status="CONFIGURED", servers=tuple(results))


async def _probe_one(config: McpServerConfig) -> McpReadyServer:
    timeout = _probe_timeout(config)
    try:
        with anyio.fail_after(timeout):
            async with build_mcp_client(config) as client:
                tools = await client.list_tools()
        return McpReadyServer(name=config.name, ok=True, tool_count=len(tools))
    except Exception as exc:
        # 只回异常类型：str(exc) 常含 URL / 上游正文
        logger.warning("MCP ready probe failed for %s: %s", config.name, type(exc).__name__)
        return McpReadyServer(name=config.name, ok=False, error=type(exc).__name__)


def has_mcp_toolsets(toolsets: list[object]) -> bool:
    # 沿 WrapperToolset.wrapped 链下探：Prefixed / _TimeoutToolset 可多层嵌套
    for item in toolsets:
        current: object | None = item
        while current is not None:
            if isinstance(current, MCPToolset):
                return True
            current = getattr(current, "wrapped", None)
    return False
