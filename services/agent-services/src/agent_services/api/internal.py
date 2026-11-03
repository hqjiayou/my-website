import base64
import binascii
import logging
from datetime import UTC, datetime
from urllib.parse import urlsplit

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic_ai.models import Model

from agent_services.agent.experience import (
    ExperienceExtractionUnavailable,
    extract_experience_lessons,
)
from agent_services.agent.harness import harness_component_flags
from agent_services.agent.knowledge_tools import KnowledgeSearchCollector
from agent_services.agent.mcp_tools import mcp_config_snapshot, probe_mcp_ready
from agent_services.agent.model_factory import (
    EntryFallback,
    ModelHandle,
    create_model_for_entry,
    public_model_spec,
)
from agent_services.agent.ocr import OcrUnavailable, transcribe_image, transcribe_pdf
from agent_services.agent.runtime import (
    BUDGET_EXCEEDED,
    ChatBlocked,
    run_budget_from_settings,
    run_chat,
)
from agent_services.agent.skills import (
    default_skills_directory,
    list_skill_catalog,
    resolve_skills,
)
from agent_services.agent.stream_runtime import stream_chat_events
from agent_services.agent.table_tools import structured_tables_available
from agent_services.agent.todo_tools import todo_store_from_request
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.agent.visualization import VisualizationCollector
from agent_services.api.errors import InternalApiError, external_failure_message
from agent_services.auth.internal_token import require_internal_service_token
from agent_services.config import Settings, retrieval_not_configured_message
from agent_services.retrieval.embeddings import (
    EmbeddingClient,
    create_embedding_client_for,
    embedding_class_name,
)
from agent_services.retrieval.runtime_config import apply_runtime_config
from agent_services.retrieval.service import (
    DimensionChangeRequired,
    EmbeddingClass,
    IndexSegment,
    IndexTable,
    IndexTableRow,
    ReembedPrepared,
    RetrievalChunkLimitExceeded,
    RetrievalService,
)
from agent_services.schemas.chat import (
    ChatCitation,
    ChatRequest,
    ChatSuccessData,
    ChatUsage,
    EmbeddingClassCredentials,
    SearchSummaryItem,
    ToolTraceItem,
)
from agent_services.schemas.experience import (
    ExperienceCandidate,
    ExperienceExtractRequest,
    ExperienceExtractResponse,
)
from agent_services.schemas.health import (
    HarnessFiles,
    HealthResponse,
    McpReadyResponse,
    McpReadyServerStatus,
    McpServerHealth,
    RuntimeLimits,
)
from agent_services.schemas.ocr import (
    OCR_IMAGE_MEDIA_TYPES,
    OcrImageRequest,
    OcrImageResponse,
    OcrPdfRequest,
    OcrPdfResponse,
)
from agent_services.schemas.retrieval import (
    ReembedPrepareRequest,
    ReembedPrepareResponse,
    RetrievalSearchHit,
    RetrievalSearchRequest,
    RetrievalSearchResponse,
    RetrievalUpsertRequest,
)
from agent_services.schemas.runtime_config import (
    RuntimeConfigPushRequest,
    RuntimeConfigPushResponse,
)
from agent_services.schemas.skills import SkillCatalogItem, SkillCatalogResponse

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(require_internal_service_token)],
)


def _model_handle(request: Request) -> ModelHandle:
    return request.app.state.model_handle


def _execution_handle(request: Request, body: ChatRequest) -> ModelHandle:
    """请求里带了 Java 模型入口就用那一条。

    没带入口时只有显式开启 AI_ALLOW_PROCESS_MODEL（本机调试）才用进程模型；
    否则返回 BLOCKED handle——所选模式无入口是「未配置」，不许进程环境模型
    静默顶替（Issue #145 F1，ADR-0004）。
    """
    selection = body.model_selection
    if selection is None:
        if not request.app.state.settings.ai_allow_process_model:
            return ModelHandle(
                model=None,
                spec="blocked",
                blocked_reason=(
                    "AI model is not configured for this request "
                    "(no model entry selected; process model fallback is disabled)."
                ),
            )
        return _model_handle(request)
    return create_model_for_entry(
        spec=selection.spec,
        api_key=selection.api_key,
        base_url=selection.base_url,
        settings=request.app.state.settings,
        fallbacks=[
            EntryFallback(spec=item.spec, api_key=item.api_key, base_url=item.base_url)
            for item in body.fallback_models
        ],
    )


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


def _raise_if_retrieval_not_configured(request: Request) -> None:
    """索引写入在检索键缺失时立刻失败，并点名缺哪些键。不带 providerStatus，避免 Java 收成通用外部故障。"""
    missing = list(getattr(request.app.state, "retrieval_missing_settings", []) or [])
    if not missing:
        return
    raise InternalApiError(
        status_code=503,
        error_code="RETRIEVAL_NOT_CONFIGURED",
        message=retrieval_not_configured_message(missing),
    )


def _request_embedder(
    request: Request, spec: str | None, api_key: str | None, base_url: str | None
) -> EmbeddingClient | None:
    """模型入口带了 embedding spec 和 key 时，这一次检索用这把密钥，不读进程 AI_PROVIDER_API_KEY。"""
    if spec is None or not spec.strip() or api_key is None or not api_key.strip():
        return None
    client, reason = create_embedding_client_for(
        spec=spec,
        api_key=api_key,
        base_url=base_url,
        dimensions=request.app.state.settings.ai_embedding_dimensions,
    )
    if client is None:
        logger.warning("model entry embedding client was not built: %s", reason)
    return client


def _java_knowledge_hints(request: Request, body: ChatRequest) -> list[str] | None:
    """Java 侧文本命中的知识片段，仅作兜底注入。

    向量检索可用时（有检索服务）知识检索由 agent 经搜索工具执行，
    这里不再预取；只有检索服务缺失、agent 无从搜索时，才注入 Java 片段。
    """
    if _retrieval_service_or_none(request) is not None:
        return None
    hints = list(body.knowledge_context or [])
    return hints or None


def _business_settings(settings: Settings, override: str | None) -> Settings:
    """请求里的回调地址覆盖进程环境变量。空则保持进程配置。"""
    if override is None or not override.strip():
        return settings
    value = override.strip().rstrip("/")
    parsed = urlsplit(value)
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
    ):
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="businessApiBaseUrl is invalid.",
        )
    return settings.model_copy(update={"business_api_base_url": value})


def _parse_default_skills(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def default_skill_ids_for_request(skills: list[str] | None, configured: str) -> list[str] | None:
    """省略 skills 时用进程默认；请求显式带了数组（含空数组）就只用这一份。"""
    if skills is not None:
        return None
    return _parse_default_skills(configured)


def _harness_dir(request: Request) -> str | None:
    raw = request.app.state.settings.agent_harness_dir.strip()
    return raw or None


def _skills_dir(request: Request) -> str:
    settings = request.app.state.settings
    return settings.agent_skills_dir.strip() or default_skills_directory()


def _embedding_classes_for(
    request: Request, classes: list[EmbeddingClassCredentials] | None
) -> list[EmbeddingClass] | None:
    """把请求里登记的类变成可调用的嵌入客户端。省略时返回 None，走原来的单套凭证。"""
    if not classes:
        return None
    resolved: list[EmbeddingClass] = []
    for item in classes:
        resolved.append(
            EmbeddingClass(
                embedding_class_name(item.embedding_spec),
                _request_embedder(request, item.embedding_spec, item.api_key, item.base_url),
            )
        )
    return resolved


def chat_embedding_credentials(body: ChatRequest) -> tuple[str | None, str | None, str | None]:
    """对话检索的嵌入凭证与回答模型分开。

    请求显式带了 embedding spec 和 key 时用这一套（两种对话模式共用）。
    否则才退回主用入口自己的 embedding spec；入口没带时返回空，调用方用进程嵌入模型。
    """
    if (
        body.embedding_spec
        and body.embedding_spec.strip()
        and body.embedding_api_key
        and body.embedding_api_key.strip()
    ):
        return body.embedding_spec, body.embedding_api_key, body.embedding_base_url
    selection = body.model_selection
    if selection is None or not (selection.embedding_spec and selection.embedding_spec.strip()):
        return None, None, None
    return selection.embedding_spec, selection.api_key, selection.base_url


async def _prepare_execution(
    request: Request,
    body: ChatRequest,
    agent_id: str,
    model: Model | None,
    *,
    visualizations: bool = False,
):
    """组装一轮执行的 skills/todos/toolsets 与收集器。

    visualizations 只给流式端点传 True：卡片靠 VISUALIZATION 事件呈现，
    同步 chat 没有事件通道，工具侧不产卡（render_chart 不注册、天气不发卡）。
    """
    settings = request.app.state.settings
    skills_dir = _skills_dir(request)
    resolution = resolve_skills(
        skills_dir=skills_dir,
        requested_skill_ids=body.skills,
        default_skill_ids=default_skill_ids_for_request(body.skills, settings.agent_default_skills),
    )
    policy = (settings.agent_skills_unknown_policy or "ignore").strip().lower()
    if policy == "reject" and resolution.has_missing:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message=f"Unknown skill ids: {', '.join(resolution.missing_ids)}",
        )
    todo_store = todo_store_from_request(body.todos)
    selection = body.model_selection
    embedding_spec, embedding_api_key, embedding_base_url = chat_embedding_credentials(body)
    embedding_classes = _embedding_classes_for(request, body.embedding_classes)
    embedder = (
        None
        if embedding_classes
        else _request_embedder(request, embedding_spec, embedding_api_key, embedding_base_url)
    )
    collector = KnowledgeSearchCollector()
    viz_collector = VisualizationCollector()
    retrieval = _retrieval_service_or_none(request)
    tables_available = await structured_tables_available(retrieval, agent_id)
    toolsets = build_execution_toolsets(
        _business_settings(settings, body.business_api_base_url),
        agent_id,
        todo_store=todo_store,
        model=model,
        retrieval=retrieval,
        retrieval_embedder=embedder,
        retrieval_embedding_model=None if embedding_classes else embedding_spec,
        retrieval_embedding_classes=embedding_classes,
        retrieval_model_selection=selection,
        knowledge_collector=collector,
        tables_available=tables_available,
        default_latitude=body.latitude,
        default_longitude=body.longitude,
        preview=body.preview,
        visualization_collector=viz_collector if visualizations else None,
    )
    return resolution, todo_store, toolsets, collector, viz_collector


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
            SkillCatalogItem(
                id=item.id,
                description=item.description,
                version=item.version,
                license=item.license,
                compatibility=item.compatibility,
                metadata=dict(item.metadata),
                allowed_tools=list(item.allowed_tools),
            )
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
    retrieval_service = _retrieval_service_or_none(request)
    retrieval_reason = getattr(request.app.state, "retrieval_blocked_reason", None)
    # blocked_reason 非空即不算就绪：启动期它只在嵌入未就绪时出现（行为不变），
    # 运行期它还承载 #146 热替换的「维度待重嵌」状态——不加这一条，维度变更后
    # health 会在 reembed 完成前谎报 LIVE。
    retrieval_ready = (
        retrieval_service is not None
        and retrieval_service.has_process_embedder
        and retrieval_reason is None
    )
    return HealthResponse(
        limits=RuntimeLimits(
            maxRequests=usage_limits.request_limit if usage_limits else None,
            maxToolCalls=usage_limits.tool_calls_limit if usage_limits else None,
            maxTotalTokens=usage_limits.total_tokens_limit if usage_limits else None,
            toolTimeoutSeconds=budget.tool_timeout_seconds,
            historyMaxChars=budget.history_max_chars,
        ),
        provider=public_model_spec(handle.spec),
        providerStatus="LIVE" if handle.live else "BLOCKED",
        fallbackProviders=[public_model_spec(spec) for spec in handle.fallback_specs],
        timestamp=datetime.now(UTC).isoformat(),
        mcpStatus=snapshot.status,
        mcpServers=[
            McpServerHealth(name=item.name, enabled=item.enabled) for item in snapshot.servers
        ],
        harness=HarnessFiles.model_validate(
            harness_component_flags(request.app.state.settings.agent_harness_dir.strip() or None)
        ),
        retrievalStatus="LIVE" if retrieval_ready else "BLOCKED",
        retrievalBlockedReason=None if retrieval_ready else retrieval_reason,
        retrievalDegraded=retrieval_service.degraded if retrieval_service is not None else False,
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


@router.put("/config/retrieval")
async def put_runtime_config(
    request: Request, body: RuntimeConfigPushRequest
) -> dict[str, object]:
    """运行配置热替换（#146 UoW-2）：Java 保存页面配置后推送，四项缺席＝未配置。

    一律回 200＋{applied, appliedVersion, blockedReason?}：Java 侧据 applied
    区分 APPLIED / BLOCKED 落状态列。入参含 DSN / key 明文，只进内存，
    不写日志、不回显（响应与原因均不带正文）。
    """
    result = await apply_runtime_config(request.app, body.to_values())
    payload = RuntimeConfigPushResponse(
        applied=result.applied,
        applied_version=result.applied_version,
        blocked_reason=result.blocked_reason,
    )
    return payload.model_dump(by_alias=True, exclude_none=True)


@router.post("/chat")
async def internal_chat(request: Request, body: ChatRequest) -> dict[str, object]:
    agent_id = body.agent_id.strip()
    message = body.message.strip()
    if not agent_id or (not message and not body.attachments and not body.documents):
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="agentId and message are required.",
        )

    handle = _execution_handle(request, body)
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )

    skill_resolution, todo_store, toolsets, collector, _viz = await _prepare_execution(
        request, body, agent_id, handle.model
    )

    outcome = await run_chat(
        handle.model,  # type: ignore[arg-type]  # live 已保证非 None
        agent_id=agent_id,
        message=message,
        attachments=body.attachments,
        documents=body.documents,
        prompt_profile=body.prompt_profile,
        history=body.history,
        retrieval_context=_java_knowledge_hints(request, body),
        retrieval_chunks=None,
        skill_instructions=skill_resolution.instructions,
        initial_todos=body.todos,
        toolsets=toolsets,
        budget=run_budget_from_settings(request.app.state.settings),
        locale=body.locale,
        timezone_name=body.timezone,
        latitude=body.latitude,
        longitude=body.longitude,
        accuracy_meters=body.accuracy_meters,
        skills_max_instruction_chars=request.app.state.settings.agent_skills_max_instruction_chars,
        skill_ids=list(skill_resolution.applied_ids),
        skills_missing=list(skill_resolution.missing_ids),
        harness_dir=_harness_dir(request),
        memories=body.memories,
        memory_enabled=body.memory_enabled,
        memory_extraction_enabled=request.app.state.settings.agent_memory_extraction_enabled,
        experience_lessons=body.experience_lessons,
        experience_lessons_enabled=request.app.state.settings.agent_experience_lessons_enabled,
        knowledge_collector=collector,
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
        retrieval_degraded=collector.degraded,
        citations=[ChatCitation.model_validate(item) for item in outcome.citations]
        if outcome.citations
        else None,
        searches=[SearchSummaryItem.model_validate(item) for item in collector.searches]
        if collector.searches
        else None,
        extracted_memories=outcome.extracted_memories,
        model=outcome.model,
    )
    payload = data.model_dump(by_alias=True)
    # 有采用才出现，与 SSE 加法字段一致；不要把 null 写进既有响应。
    if not payload.get("citations"):
        payload.pop("citations", None)
    if not payload.get("extractedMemories"):
        payload.pop("extractedMemories", None)
    return {"ok": True, "data": payload}



@router.post("/ocr/pdf")
async def internal_ocr_pdf(request: Request, body: OcrPdfRequest) -> dict[str, object]:
    """扫描版 PDF 转写：模型入口链与 chat 同形状（_execution_handle 只读 model/fallback 两字段）。"""
    handle = _execution_handle(request, body)  # type: ignore[arg-type]
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )
    try:
        pdf_bytes = base64.b64decode(body.content_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise InternalApiError(
            status_code=400,
            error_code="INVALID_PDF",
            message="contentBase64 is not valid base64.",
        ) from exc
    if not pdf_bytes.startswith(b"%PDF"):
        raise InternalApiError(
            status_code=400,
            error_code="INVALID_PDF",
            message="content is not a PDF document.",
        )
    try:
        text = await transcribe_pdf(handle.model, pdf_bytes)  # type: ignore[arg-type]
    except OcrUnavailable as exc:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=str(exc),
            provider_status="BLOCKED",
        ) from exc
    payload = OcrPdfResponse(text=text, model_spec=handle.spec)
    return {"ok": True, "data": payload.model_dump(by_alias=True)}


def _image_magic_matches(media_type: str, data: bytes) -> bool:
    if media_type == "image/png":
        return data.startswith(b"\x89PNG\r\n\x1a\n")
    if media_type == "image/jpeg":
        return data.startswith(b"\xff\xd8\xff")
    if media_type == "image/gif":
        return data.startswith((b"GIF87a", b"GIF89a"))
    if media_type == "image/webp":
        return len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP"
    if media_type == "image/bmp":
        return data.startswith(b"BM")
    if media_type in ("image/heic", "image/heif"):
        # ISO BMFF：ftyp 盒 + heif 家族品牌
        brands = (b"heic", b"heix", b"hevc", b"heim", b"heis", b"hevm", b"mif1", b"mif2", b"msf1")
        return len(data) >= 12 and data[4:8] == b"ftyp" and data[8:12] in brands
    return False


@router.post("/ocr/image")
async def internal_ocr_image(request: Request, body: OcrImageRequest) -> dict[str, object]:
    """图片转写（知识库图片上传）：与 /ocr/pdf 同形状，输入换成图片字节＋媒体类型。"""
    handle = _execution_handle(request, body)  # type: ignore[arg-type]
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )
    if body.media_type not in OCR_IMAGE_MEDIA_TYPES:
        raise InternalApiError(
            status_code=400,
            error_code="INVALID_IMAGE",
            message=f"unsupported media type: {body.media_type}",
        )
    try:
        image_bytes = base64.b64decode(body.content_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise InternalApiError(
            status_code=400,
            error_code="INVALID_IMAGE",
            message="contentBase64 is not valid base64.",
        ) from exc
    if not _image_magic_matches(body.media_type, image_bytes):
        raise InternalApiError(
            status_code=400,
            error_code="INVALID_IMAGE",
            message="content does not match the declared media type.",
        )
    try:
        text = await transcribe_image(handle.model, image_bytes, body.media_type)  # type: ignore[arg-type]
    except OcrUnavailable as exc:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=str(exc),
            provider_status="BLOCKED",
        ) from exc
    payload = OcrImageResponse(text=text, model_spec=handle.spec)
    return {"ok": True, "data": payload.model_dump(by_alias=True)}


@router.post("/experience/extract")
async def internal_experience_extract(
    request: Request, body: ExperienceExtractRequest
) -> dict[str, object]:
    """经验提炼（#184 UoW-2）：对 Java 预筛出的信号轮跑一次独立提炼。

    模型入口链与 ocr 同形状（_execution_handle 只读 model/fallback 两字段）。
    与记忆抽取不同，失败诚实回 BLOCKED——调用方按批次失败计，不许空数组冒充无可提炼。
    """
    handle = _execution_handle(request, body)  # type: ignore[arg-type]
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )
    try:
        extraction = await extract_experience_lessons(
            handle.model,  # type: ignore[arg-type]  # live 已保证非 None
            turns=body.turns,
            existing_titles=body.existing_titles,
        )
    except ExperienceExtractionUnavailable as exc:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=str(exc),
            provider_status="BLOCKED",
        ) from exc
    payload = ExperienceExtractResponse(
        candidates=[
            ExperienceCandidate(
                source_turn_id=item.source_turn_id,
                title=item.title,
                symptom=item.symptom,
                lesson=item.lesson,
                evidence=item.evidence,
            )
            for item in extraction.candidates
        ],
        usage=ChatUsage.model_validate(extraction.usage),
        model_spec=handle.spec,
    )
    return {"ok": True, "data": payload.model_dump(by_alias=True)}


@router.post("/chat/stream")
async def internal_chat_stream(request: Request, body: ChatRequest) -> StreamingResponse:
    agent_id = body.agent_id.strip()
    message = body.message.strip()
    if not agent_id or (not message and not body.attachments and not body.documents):
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="agentId and message are required.",
        )

    handle = _execution_handle(request, body)
    if not handle.live:
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=handle.blocked_reason or "AI provider is not configured.",
            provider_status="BLOCKED",
        )

    (
        skill_resolution,
        todo_store,
        toolsets,
        collector,
        viz_collector,
    ) = await _prepare_execution(request, body, agent_id, handle.model, visualizations=True)

    async def event_generator():
        async for event in stream_chat_events(
            handle.model,  # type: ignore[arg-type]
            agent_id=agent_id,
            message=message,
            attachments=body.attachments,
            documents=body.documents,
            prompt_profile=body.prompt_profile,
            history=body.history,
            retrieval_context=_java_knowledge_hints(request, body),
            retrieval_chunks=None,
            retrieval_degraded=False,
            skill_instructions=skill_resolution.instructions,
            initial_todos=body.todos,
            toolsets=toolsets,
            todo_store=todo_store,
            skills_applied=list(skill_resolution.applied_ids),
            skills_missing=list(skill_resolution.missing_ids),
            budget=run_budget_from_settings(request.app.state.settings),
            locale=body.locale,
            timezone_name=body.timezone,
            latitude=body.latitude,
            longitude=body.longitude,
            accuracy_meters=body.accuracy_meters,
            skills_max_instruction_chars=request.app.state.settings.agent_skills_max_instruction_chars,
            skill_ids=list(skill_resolution.applied_ids),
            harness_dir=_harness_dir(request),
            memories=body.memories,
            memory_enabled=body.memory_enabled,
            memory_extraction_enabled=request.app.state.settings.agent_memory_extraction_enabled,
            experience_lessons=body.experience_lessons,
            experience_lessons_enabled=request.app.state.settings.agent_experience_lessons_enabled,
            knowledge_collector=collector,
            visualization_collector=viz_collector,
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
    agent_id = body.agent_id.strip()
    source_id = body.source_id.strip()
    if not agent_id or not source_id or not body.content.strip():
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="agentId, sourceId and content are required.",
        )
    _raise_if_retrieval_not_configured(request)
    service = _require_retrieval_service(request)
    try:
        embedding_classes = _embedding_classes_for(request, body.embedding_classes)
        chunk_count = await service.upsert_document(
            agent_id=agent_id,
            source_id=source_id,
            title=body.title,
            content=body.content,
            embedder=(
                None
                if embedding_classes
                else _request_embedder(request, body.embedding_spec, body.api_key, body.base_url)
            ),
            embedding_model=None if embedding_classes else body.embedding_spec,
            embedding_classes=embedding_classes,
            segments=None
            if body.segments is None
            else [
                IndexSegment(
                    text=item.text,
                    page=item.page,
                    sheet=item.sheet,
                    row_start=item.row_start,
                    row_end=item.row_end,
                )
                for item in body.segments
            ],
            tables=None
            if body.tables is None
            else [
                IndexTable(
                    sheet=item.sheet,
                    header_row=item.header_row,
                    headers=list(item.headers),
                    rows=[
                        IndexTableRow(row_number=row.row_number, cells=list(row.cells))
                        for row in item.rows
                    ],
                    truncated=item.truncated,
                )
                for item in body.tables
            ],
        )
    except RetrievalChunkLimitExceeded as exc:
        raise InternalApiError(status_code=400, error_code="BAD_REQUEST", message=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 — embedding/DB 故障对 Java 诚实报 503
        logger.warning("Retrieval index update failed", exc_info=exc)
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=external_failure_message("Retrieval index update failed", exc),
            provider_status="BLOCKED",
        ) from exc
    return {"ok": True, "data": {"chunks": chunk_count}}


@router.post("/retrieval/reembed/prepare")
async def prepare_retrieval_reembed(
    request: Request, body: ReembedPrepareRequest
) -> dict[str, object]:
    """探针目标 embedding 维度。列维度不同且未确认时 409，不改表。"""
    _raise_if_retrieval_not_configured(request)
    service = _require_retrieval_service(request)
    selection = body.model_selection
    embedder = (
        _request_embedder(request, selection.embedding_spec, selection.api_key, selection.base_url)
        if selection is not None
        else None
    )
    try:
        prepared: ReembedPrepared = await service.prepare_reembed(
            embedder=embedder,
            confirm_recreate=body.confirm_recreate,
        )
    except DimensionChangeRequired as exc:
        raise InternalApiError(
            status_code=409,
            error_code="DIMENSION_CHANGE_REQUIRED",
            message="Embedding dimensions differ from the vector column; confirm recreate to rebuild.",
            details={"dimensions": exc.dimensions, "columnDimensions": exc.column_dimensions},
        ) from exc
    except Exception as exc:  # noqa: BLE001 — 无 embedder / embedding / DB 故障对 Java 诚实报 503
        logger.warning("Retrieval reembed prepare failed", exc_info=exc)
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=external_failure_message("Retrieval reembed prepare failed", exc),
            provider_status="BLOCKED",
        ) from exc
    return ReembedPrepareResponse(
        dimensions=prepared.dimensions,
        column_dimensions=prepared.column_dimensions,
        recreated=prepared.recreated,
    ).model_dump(by_alias=True)


@router.delete("/retrieval/documents")
async def delete_retrieval_document(
    request: Request, agentId: str, sourceId: str
) -> dict[str, object]:
    agent_id = agentId.strip()
    source_id = sourceId.strip()
    if not agent_id or not source_id:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="agentId and sourceId are required.",
        )
    _raise_if_retrieval_not_configured(request)
    service = _require_retrieval_service(request)
    try:
        await service.delete_document(agent_id=agent_id, source_id=source_id)
    except Exception as exc:  # noqa: BLE001
        logger.warning("Retrieval index delete failed", exc_info=exc)
        raise InternalApiError(
            status_code=503,
            error_code="EXTERNAL_DEPENDENCY_BLOCKED",
            message=external_failure_message("Retrieval index delete failed", exc),
            provider_status="BLOCKED",
        ) from exc
    return {"ok": True, "data": None}


@router.post("/retrieval/search")
async def search_retrieval(request: Request, body: RetrievalSearchRequest) -> dict[str, object]:
    """内部知识检索。嵌入或向量库失败时沿用 retrieve_chunks 的 degraded，不回 500。"""
    agent_id = body.agent_id.strip()
    query = body.query.strip()
    if not query:
        raise InternalApiError(
            status_code=422,
            error_code="BAD_REQUEST",
            message="query must not be blank.",
        )
    if not agent_id:
        raise InternalApiError(
            status_code=400,
            error_code="BAD_REQUEST",
            message="agentId is required.",
        )
    service = _require_retrieval_service(request)
    embedding_classes = _embedding_classes_for(request, body.embedding_classes)
    chunks = await service.retrieve_chunks(
        agent_id=agent_id,
        query=query,
        top_k=body.top_k,
        embedder=(
            None
            if embedding_classes
            else _request_embedder(request, body.embedding_spec, body.api_key, body.base_url)
        ),
        embedding_model=None if embedding_classes else body.embedding_spec,
        embedding_classes=embedding_classes,
        model_selection=body.model_selection,
    )
    payload = RetrievalSearchResponse(
        hits=[
            RetrievalSearchHit(
                source_id=chunk.source_id,
                chunk_index=chunk.chunk_index,
                score=chunk.score,
                content=chunk.content,
            )
            for chunk in chunks
        ],
        degraded=service.degraded,
        rerank_applied=service.rerank_applied,
    )
    dumped = payload.model_dump(by_alias=True)
    # 未重排时不写出默认 false，避免既有 {hits, degraded} 精确断言被改写。
    if not service.rerank_applied:
        dumped.pop("rerankApplied", None)
    return dumped
