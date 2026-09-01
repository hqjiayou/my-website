from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from agent_services.agent.skills import default_skills_directory, resolve_skill_instructions
from agent_services.agent.todo_tools import todo_store_from_request
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.runtime import ChatBlocked, run_chat
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.api.errors import InternalApiError
from agent_services.auth.internal_token import require_internal_service_token
from agent_services.retrieval.service import RetrievalService
from agent_services.schemas.chat import ChatRequest
from agent_services.schemas.health import HealthResponse
from agent_services.schemas.retrieval import RetrievalUpsertRequest

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


def _prepare_execution(request: Request, body: ChatRequest, site_id: str):
    settings = request.app.state.settings
    skills_dir = settings.agent_skills_dir.strip() or default_skills_directory()
    skill_instructions = resolve_skill_instructions(
        skills_dir=skills_dir,
        requested_skill_ids=body.skills,
        default_skill_ids=_parse_default_skills(settings.agent_default_skills),
    )
    todo_store = todo_store_from_request(body.todos)
    toolsets = build_execution_toolsets(settings, site_id, todo_store=todo_store)
    return skill_instructions, todo_store, toolsets


@router.get("/health")
async def internal_health(request: Request) -> HealthResponse:
    handle = _model_handle(request)
    return HealthResponse(
        provider=handle.spec,
        providerStatus="LIVE" if handle.live else "BLOCKED",
        timestamp=datetime.now(UTC).isoformat(),
    )


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

    skill_instructions, todo_store, toolsets = _prepare_execution(request, body, site_id)

    outcome = await run_chat(
        handle.model,  # type: ignore[arg-type]  # live 已保证非 None
        site_id=site_id,
        message=message,
        prompt_profile=body.prompt_profile,
        history=body.history,
        retrieval_context=await _retrieval_context(request, site_id, message),
        skill_instructions=skill_instructions,
        initial_todos=body.todos,
        toolsets=toolsets,
    )

    if isinstance(outcome, ChatBlocked):
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=outcome.message,
            provider_status="BLOCKED",
        )

    data: dict[str, object] = {
        "reply": outcome.reply,
        "conversationId": body.conversation_id,
        "providerStatus": "LIVE",
    }
    todo_snapshot = todo_store.snapshot()
    if todo_snapshot.items:
        data["todos"] = todo_snapshot.to_api()

    return {"ok": True, "data": data}


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
    skill_instructions, todo_store, toolsets = _prepare_execution(request, body, site_id)

    async def event_generator():
        async for event in stream_chat_events(
            handle.model,  # type: ignore[arg-type]
            site_id=site_id,
            message=message,
            prompt_profile=body.prompt_profile,
            history=body.history,
            retrieval_context=retrieval_context,
            skill_instructions=skill_instructions,
            initial_todos=body.todos,
            toolsets=toolsets,
            todo_store=todo_store,
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
