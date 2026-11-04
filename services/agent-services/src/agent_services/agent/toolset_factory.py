"""组合 Phase 4 business tools + 知识搜索 + Phase 5 todo/MCP toolsets，以及本 run 的委派工具。"""

from __future__ import annotations

from pydantic_ai.models import Model

from agent_services.agent.background_tasks import build_background_toolset
from agent_services.agent.business_tools import build_business_toolset
from agent_services.agent.chart_tools import build_chart_toolset
from agent_services.agent.knowledge_tools import (
    KnowledgeSearchCollector,
    build_knowledge_toolset,
)
from agent_services.agent.mcp_tools import build_mcp_toolsets, has_mcp_toolsets
from agent_services.agent.skill_tools import SkillLoadCollector, build_skill_toolset
from agent_services.agent.subagent import build_delegate_tool
from agent_services.agent.table_tools import build_table_toolset
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.agent.visualization import VisualizationCollector
from agent_services.agent.weather_tools import build_weather_toolset
from agent_services.config import Settings
from agent_services.retrieval.embeddings import EmbeddingClient
from agent_services.retrieval.service import EmbeddingClass, RetrievalService
from agent_services.schemas.chat import ModelSelection
from agent_services.tools.business_api import BusinessApiClient


def build_execution_toolsets(
    settings: Settings,
    agent_id: str,
    *,
    todo_store: TodoStore,
    client: BusinessApiClient | None = None,
    model: Model | None = None,
    retrieval: RetrievalService | None = None,
    retrieval_embedder: EmbeddingClient | None = None,
    retrieval_embedding_model: str | None = None,
    retrieval_embedding_classes: list[EmbeddingClass] | None = None,
    retrieval_model_selection: ModelSelection | None = None,
    knowledge_collector: KnowledgeSearchCollector | None = None,
    tables_available: bool = False,
    default_latitude: float | None = None,
    default_longitude: float | None = None,
    preview: bool = False,
    visualization_collector: VisualizationCollector | None = None,
    mcp_server_names: list[str] | None = None,
    skills_dir: str | None = None,
    skill_load_collector: SkillLoadCollector | None = None,
    skill_preselected_ids: list[str] | None = None,
) -> list[object]:
    toolsets: list[object] = []
    # Java internal 通道全 run 共用一个 client：business tools 与知识文档枚举（#181）同源。
    # 未注入且未配置 BUSINESS_API_BASE_URL 时为 None：business toolset 与枚举工具都不注册，
    # 不给模型一个注定失败的工具，更不伪造空清单。
    java_client = client
    if java_client is None and settings.business_api_base_url.strip():
        java_client = BusinessApiClient(
            settings.business_api_base_url, settings.agent_internal_service_token
        )
    business = build_business_toolset(settings, agent_id, client=java_client)
    if business is not None:
        toolsets.append(business)
    if retrieval is not None:
        # 知识搜索紧跟业务工具：两者都是「查本站资料」，且不依赖 todo / 子 agent 开关，
        # 主 agent 任何一轮都能直接查知识库，不必先委派。
        # 文档枚举是只读基础能力，不分正式/试聊（preview 不门控，#181 定案）。
        toolsets.append(
            build_knowledge_toolset(
                agent_id,
                retrieval,
                embedder=retrieval_embedder,
                embedding_model=retrieval_embedding_model,
                embedding_classes=retrieval_embedding_classes,
                model_selection=retrieval_model_selection,
                collector=knowledge_collector,
                client=java_client,
            )
        )
        if tables_available:
            toolsets.append(build_table_toolset(agent_id, retrieval, collector=knowledge_collector))
    # 天气工具无条件注册：无定位时工具自身回错误文本，不靠注册与否表达可用性。
    # 天气卡与 render_chart 只在 preview 且调用方给了卡片收集器时启用（Issue #179）：
    # 正式对话的工具集与改动前逐字一致。
    cards_active = preview and visualization_collector is not None
    toolsets.append(
        build_weather_toolset(
            default_latitude=default_latitude,
            default_longitude=default_longitude,
            card_collector=visualization_collector if cards_active else None,
        )
    )
    if cards_active:
        toolsets.append(build_chart_toolset(visualization_collector))  # type: ignore[arg-type]
    if settings.agent_enable_todo_tools:
        toolsets.append(build_todo_toolset(todo_store))
    if skill_load_collector is not None and skills_dir:
        # 模型自选 skill（Issue #195）：收集器由调用方按
        # AGENT_SKILLS_SELF_SELECT_ENABLED 决定是否创建；None 即不注册，
        # 工具面与未启用时逐字一致。预选集传入工具做重复加载去重。
        toolsets.append(
            build_skill_toolset(
                skills_dir,
                collector=skill_load_collector,
                preselected_ids=skill_preselected_ids or (),
                max_instruction_chars=settings.agent_skills_max_instruction_chars,
            )
        )
    # per-agent 名字过滤（Issue #192）：None 不过滤、空集不注册、非空取交集；
    # harness 的 MCP 名单段从实际 toolsets 反推，自动跟随过滤结果。
    toolsets.extend(
        build_mcp_toolsets(
            settings,
            only_names=None if mcp_server_names is None else set(mcp_server_names),
        )
    )
    if settings.agent_subagent_enabled and model is not None:
        toolsets.append(
            build_delegate_tool(
                model,
                agent_id=agent_id,
                retrieval=retrieval,
                embedder=retrieval_embedder,
                embedding_model=retrieval_embedding_model,
                embedding_classes=retrieval_embedding_classes,
                max_per_run=settings.agent_subagent_max_per_run,
                timeout_seconds=settings.agent_subagent_timeout_seconds,
                max_requests=settings.agent_subagent_max_requests,
            )
        )
        if settings.agent_background_tasks_enabled:
            # 后台委派（Issue #188）：与同步委派同一 gating（子 agent 总开关＋模型），
            # 再叠自己的总开关；关时不注册，组装逐字回归。注册表是进程级单例，
            # 任务跨请求存活，本轮派发、后续轮次取回。
            toolsets.append(
                build_background_toolset(
                    model,
                    agent_id=agent_id,
                    retrieval=retrieval,
                    embedder=retrieval_embedder,
                    embedding_model=retrieval_embedding_model,
                    embedding_classes=retrieval_embedding_classes,
                    max_concurrent=settings.agent_background_task_max_concurrent,
                    timeout_seconds=settings.agent_background_task_timeout_seconds,
                    max_requests=settings.agent_subagent_max_requests,
                    ttl_seconds=settings.agent_background_task_ttl_seconds,
                )
            )
    return toolsets


def execution_needs_agent_context(toolsets: list[object]) -> bool:
    return has_mcp_toolsets(toolsets)
