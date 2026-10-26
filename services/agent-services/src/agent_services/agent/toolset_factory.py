"""组合 Phase 4 business tools + 知识搜索 + Phase 5 todo/MCP toolsets，以及本 run 的委派工具。"""

from __future__ import annotations

from pydantic_ai.models import Model

from agent_services.agent.business_tools import build_business_toolset
from agent_services.agent.knowledge_tools import build_knowledge_toolset
from agent_services.agent.mcp_tools import build_mcp_toolsets, has_mcp_toolsets
from agent_services.agent.subagent import build_delegate_tool
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.config import Settings
from agent_services.retrieval.service import RetrievalService
from agent_services.tools.business_api import BusinessApiClient


def build_execution_toolsets(
    settings: Settings,
    site_id: str,
    *,
    todo_store: TodoStore,
    client: BusinessApiClient | None = None,
    model: Model | None = None,
    retrieval: RetrievalService | None = None,
) -> list[object]:
    toolsets: list[object] = []
    business = build_business_toolset(settings, site_id, client=client)
    if business is not None:
        toolsets.append(business)
    if retrieval is not None:
        # 知识搜索紧跟业务工具：两者都是「查本站资料」，且不依赖 todo / 子 agent 开关，
        # 主 agent 任何一轮都能直接查知识库，不必先委派。
        toolsets.append(build_knowledge_toolset(site_id, retrieval))
    if settings.agent_enable_todo_tools:
        toolsets.append(build_todo_toolset(todo_store))
    toolsets.extend(build_mcp_toolsets(settings))
    if settings.agent_subagent_enabled and model is not None:
        toolsets.append(
            build_delegate_tool(
                model,
                site_id=site_id,
                retrieval=retrieval,
                max_per_run=settings.agent_subagent_max_per_run,
                timeout_seconds=settings.agent_subagent_timeout_seconds,
                max_requests=settings.agent_subagent_max_requests,
            )
        )
    return toolsets


def execution_needs_agent_context(toolsets: list[object]) -> bool:
    return has_mcp_toolsets(toolsets)
