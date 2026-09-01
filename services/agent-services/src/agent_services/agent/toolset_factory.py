"""组合 Phase 4 business tools + Phase 5 todo/MCP toolsets。"""

from __future__ import annotations

from agent_services.agent.business_tools import build_business_toolset
from agent_services.agent.mcp_tools import build_mcp_toolsets, has_mcp_toolsets
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.config import Settings
from agent_services.tools.business_api import BusinessApiClient


def build_execution_toolsets(
    settings: Settings,
    site_id: str,
    *,
    todo_store: TodoStore,
    client: BusinessApiClient | None = None,
) -> list[object]:
    toolsets: list[object] = []
    business = build_business_toolset(settings, site_id, client=client)
    if business is not None:
        toolsets.append(business)
    if settings.agent_enable_todo_tools:
        toolsets.append(build_todo_toolset(todo_store))
    toolsets.extend(build_mcp_toolsets(settings))
    return toolsets


def execution_needs_agent_context(toolsets: list[object]) -> bool:
    return has_mcp_toolsets(toolsets)
