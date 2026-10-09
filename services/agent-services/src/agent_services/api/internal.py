from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse, StreamingResponse

from agent_services.agent.skills import (
    default_skills_directory,
    list_skill_catalog,
    resolve_skills,
)
from agent_services.agent.todo_tools import todo_store_from_request
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.agent.mcp_tools import mcp_config_snapshot, probe_mcp_ready
from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.runtime import (
    BUDGET_EXCEEDED,
    ChatBlocked,
    run_budget_from_settings,
    run_chat,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.api.errors import InternalApiError
from agent_services.auth.internal_token import require_internal_service_token
from agent_services.retrieval.service import RetrievalService
from agent_services.schemas.chat import ChatRequest, ChatSuccessData, ChatUsage, ToolTraceItem
from agent_services.schemas.health import (
    HealthResponse,
    McpReadyResponse,
    McpReadyServerStatus,
    McpServerHealth,
    RuntimeLimits,
)
from agent_services.schemas.retrieval import RetrievalUpsertRequest
from agent_services.schemas.skills import SkillCatalogItem, SkillCatalogResponse

router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(require_internal_service_token)],
)


def _model_handle(request: Request) -> ModelHandle:
    return request.app.state.model_handle


def _retrieval_service_or_none(request: Request) -> RetrievalService | None:
    return request.app.state.retrieval_service


def _require_retrieval_service(request: Request) -> RetrievalService:
    service = _retrieval_service_or_none(request)
    if service is None:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=request.app.state.retrieval_blocked_reason
            or "Retrieval is not configured.",
            provider_status="BLOCKED",
        )
    return service


async def _retrieval_context(request: Request, site_id: str, message: str) -> list[str] | None:
    service = _retrieval_service_or_none(request)
    if service is None:
        return None
    return await service.retrieve_context(site_id=site_id, query=message)


def _parse_default_skills(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def _skills_dir(request: Request) -> str:
    settings = request.app.state.settings
    return settings.agent_skills_dir.strip() or default_skills_directory()


def _prepare_execution(request: Request, body: ChatRequest, site_id: str):
    settings = request.app.state.settings
    skills_dir = _skills_dir(request)
    resolution = resolve_skills(
        skills_dir=skills_dir,
        requested_skill_ids=body.skills,
        default_skill_ids=_parse_default_skills(settings.agent_default_skills),
    )
    policy = (settings.agent_skills_unknown_policy or "ignore").strip().lower()
    if policy == "reject" and resolution.has_missing:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message=f"Unknown skill ids: {', '.join(resolution.missing_ids)}",
        )
    todo_store = todo_store_from_request(body.todos)
    toolsets = build_execution_toolsets(settings, site_id, todo_store=todo_store)
    return resolution, todo_store, toolsets


def _blocked_to_error(outcome: ChatBlocked) -> InternalApiError:
    if outcome.error_code == BUDGET_EXCEEDED:
        # 请求本身可处理但超出本轮预算：非依赖故障，不带 providerStatus=BLOCKED
        return InternalApiError(status_code=422, error_code=BUDGET_EXCEEDED, message=outcome.message)
    return InternalApiError(
        status_code=503,
        error_code=outcome.error_code,
        message=outcome.message,
        provider_status="BLOCKED" if outcome.error_code != "MCP_UNAVAILABLE" else None,
    )


@router.get("/skills")
async def list_skills(request: Request) -> dict[str, object]:
    """技能目录（平台能力）；供 Java/预览选择 id，不返回正文（防大 payload）。"""
    catalog = list_skill_catalog(_skills_dir(request))
    payload = SkillCatalogResponse(
        skills=[
            SkillCatalogItem(id=item.id, description=item.description, version=item.version)
            for item in catalog
        ]
    )
    return {"ok": True, "data": payload.model_dump(by_alias=True)}


@router.get("/health")
async def internal_health(request: Request) -> HealthResponse:
    handle = _model_handle(request)
    snapshot = mcp_config_snapshot(request.app.state.settings)
    budget = run_budget_from_settings(request.app.state.settings)
    usage_limits = budget.usage_limits
    return HealthResponse(
        limits=RuntimeLimits(
            maxRequests=usage_limits.request_limit if usage_limits else None,
            maxToolCalls=usage_limits.tool_calls_limit if usage_limits else None,
            maxTotalTokens=usage_limits.total_tokens_limit if usage_limits else None,
            toolTimeoutSeconds=budget.tool_timeout_seconds,
            historyMaxChars=budget.history_max_chars,
        ),
        provider=handle.spec,
        providerStatus="LIVE" if handle.live else "BLOCKED",
        fallbackProviders=list(handle.fallback_specs),
        timestamp=datetime.now(UTC).isoformat(),
        mcpStatus=snapshot.status,
        mcpServers=[
            McpServerHealth(name=item.name, enabled=item.enabled) for item in snapshot.servers
        ],
    )


@router.get("/mcp/ready")
async def mcp_ready(request: Request) -> JSONResponse:
    """可选深检：对 enabled MCP 做短超时 list_tools。不替代 /health。"""
    report = await probe_mcp_ready(request.app.state.settings)
    payload = McpReadyResponse(
        ok=report.ok,
        mcpStatus=report.status,
        servers=[
            McpReadyServerStatus(
                name=item.name,
                ok=item.ok,
                toolCount=item.tool_count,
                error=item.error,
            )
            for item in report.servers
        ],
        errorCode=None if report.ok else "MCP_UNAVAILABLE",
        message=report.message,
    )
    body = payload.model_dump(by_alias=True, exclude_none=True)
    return JSONResponse(status_code=200 if report.ok else 503, content=body)


@router.post("/chat")
async def internal_chat(request: Request, body: ChatRequest) -> dict[str, object]:
    site_id = body.site_id.strip()
    message = body.message.strip()
    if not site_id or not message:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="siteId and message are required.",
        )

    handle = _model_handle(request)
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )

    skill_resolution, todo_store, toolsets = _prepare_execution(request, body, site_id)

    outcome = await run_chat(
        handle.model,  # type: ignore[arg-type]  # live 已保证非 None
        site_id=site_id,
        message=message,
        prompt_profile=body.prompt_profile,
        history=body.history,
        retrieval_context=await _retrieval_context(request, site_id, message),
        skill_instructions=skill_resolution.instructions,
        initial_todos=body.todos,
        toolsets=toolsets,
        budget=run_budget_from_settings(request.app.state.settings),
    )

    if isinstance(outcome, ChatBlocked):
        raise _blocked_to_error(outcome)

    todo_snapshot = todo_store.snapshot()
    data = ChatSuccessData(
        reply=outcome.reply,
        conversation_id=body.conversation_id,
        provider_status="LIVE",
        usage=ChatUsage.model_validate(outcome.usage),
        tools=[ToolTraceItem.model_validate(item) for item in outcome.tools],
        skills_applied=list(skill_resolution.applied_ids),
        skills_missing=list(skill_resolution.missing_ids) or None,
        todos=todo_snapshot.items or None,
    )
    return {"ok": True, "data": data.model_dump(by_alias=True)}


@router.post("/chat/stream")
async def internal_chat_stream(request: Request, body: ChatRequest) -> StreamingResponse:
    site_id = body.site_id.strip()
    message = body.message.strip()
    if not site_id or not message:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="siteId and message are required.",
        )

    handle = _model_handle(request)
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )

    retrieval_context = await _retrieval_context(request, site_id, message)
    skill_resolution, todo_store, toolsets = _prepare_execution(request, body, site_id)

    async def event_generator():
        async for event in stream_chat_events(
            handle.model,  # type: ignore[arg-type]
            site_id=site_id,
            message=message,
            prompt_profile=body.prompt_profile,
            history=body.history,
            retrieval_context=retrieval_context,
            skill_instructions=skill_resolution.instructions,
            initial_todos=body.todos,
            toolsets=toolsets,
            todo_store=todo_store,
            skills_applied=list(skill_resolution.applied_ids),
            skills_missing=list(skill_resolution.missing_ids),
            budget=run_budget_from_settings(request.app.state.settings),
        ):
            yield event.to_sse()

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.put("/retrieval/documents")
async def upsert_retrieval_document(
    request: Request, body: RetrievalUpsertRequest
) -> dict[str, object]:
    site_id = body.site_id.strip()
    source_id = body.source_id.strip()
    if not site_id or not source_id or not body.content.strip():
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="siteId, sourceId and content are required.",
        )
    service = _require_retrieval_service(request)
    try:
        chunk_count = await service.upsert_document(
            site_id=site_id, source_id=source_id, title=body.title, content=body.content
        )
    except Exception as exc:  # noqa: BLE001 — embedding/DB 故障对 Java 诚实报 503
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=f"Retrieval index update failed: {exc}",
            provider_status="BLOCKED",
        ) from exc
    return {"ok": True, "data": {"chunks": chunk_count}}


@router.delete("/retrieval/documents")
async def delete_retrieval_document(
    request: Request, siteId: str, sourceId: str
) -> dict[str, object]:
    site_id = siteId.strip()
    source_id = sourceId.strip()
    if not site_id or not source_id:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="siteId and sourceId are required.",
        )
    service = _require_retrieval_service(request)
    try:
        await service.delete_document(site_id=site_id, source_id=source_id)
    except Exception as exc:  # noqa: BLE001
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=f"Retrieval index delete failed: {exc}",
            provider_status="BLOCKED",
        ) from exc
    return {"ok": True, "data": None}
