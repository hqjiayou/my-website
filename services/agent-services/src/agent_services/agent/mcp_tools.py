"""Phase 5：MCP — 外部工具经 MCPToolset 接入（配置驱动，可选）。"""

from __future__ import annotations

import json
import logging
from typing import Any

from pydantic_ai.mcp import MCPToolset

from agent_services.config import Settings

logger = logging.getLogger(__name__)


def _parse_mcp_servers(raw: str) -> list[dict[str, Any]]:
    if not raw.strip():
        return []
    data = json.loads(raw)
    if not isinstance(data, list):
        raise ValueError("MCP_SERVERS_JSON must be a JSON array")
    return [entry for entry in data if isinstance(entry, dict)]


def build_mcp_toolsets(settings: Settings) -> list[MCPToolset]:
    """缺失或空配置 → 不注册 MCP 工具（纯问答 / 仅 business+todo 仍可用）。"""
    try:
        entries = _parse_mcp_servers(settings.mcp_servers_json)
    except (json.JSONDecodeError, ValueError) as exc:
        logger.warning("Invalid MCP_SERVERS_JSON, skipping MCP toolsets: %s", exc)
        return []

    toolsets: list[MCPToolset] = []
    for index, entry in enumerate(entries):
        name = str(entry.get("name") or f"mcp{index + 1}").strip()
        url = entry.get("url")
        if not url or not str(url).strip():
            logger.warning("MCP entry %r missing url, skipped", name)
            continue
        toolsets.append(MCPToolset(str(url).strip()).prefixed(name))
    return toolsets


def has_mcp_toolsets(toolsets: list[object]) -> bool:
    from pydantic_ai.mcp import MCPToolset

    for item in toolsets:
        if isinstance(item, MCPToolset):
            return True
        wrapped = getattr(item, "wrapped", None)
        if isinstance(wrapped, MCPToolset):
            return True
    return False
