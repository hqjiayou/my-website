import base64
import binascii
import math
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints, field_validator, model_serializer

from agent_services.retrieval.embeddings import limit_embedding_class_specs
from agent_services.schemas.todo import MAX_TODO_ITEMS, TodoItem

# 纵深防御上限（§21.4）：Java DTO 先行校验，这里防止 internal 调用方失误撑爆上下文
MAX_MESSAGE_CHARS = 16000
MAX_HISTORY_ITEMS = 50
MAX_SKILL_IDS = 16
MAX_SKILL_ID_LENGTH = 64
MAX_ATTACHMENTS = 3
MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024
MAX_ATTACHMENTS_TOTAL_BYTES = 5 * 1024 * 1024
MAX_MEMORIES = 20
MAX_MEMORY_CHARS = 500
# 本轮文档上下文（Java 站点层已抽取文本后传入；Python 只做纵深防御上限）。
MAX_DOCUMENTS = 3
MAX_DOCUMENT_NAME_CHARS = 200
MAX_DOCUMENT_TEXT_CHARS = 60_000

ImageMediaType = Literal["image/jpeg", "image/png", "image/webp", "image/gif"]

SkillId = Annotated[str, StringConstraints(max_length=MAX_SKILL_ID_LENGTH)]


class PromptProfile(BaseModel):
    system_prompt: str | None = Field(default=None, alias="systemPrompt")
    temperature: float | None = None

    model_config = {"populate_by_name": True}

    @field_validator("system_prompt")
    @classmethod
    def cap_system_prompt(cls, value: str | None) -> str | None:
        if value is None or len(value) <= MAX_MESSAGE_CHARS:
            return value
        return value[:MAX_MESSAGE_CHARS]

    @field_validator("temperature")
    @classmethod
    def drop_unusable_temperature(cls, value: float | None) -> float | None:
        # 管理端写入范围是 0–2。库里的非法值若直接 400，站点每一轮对话都会失败。
        if value is None or isinstance(value, bool) or not math.isfinite(value) or value < 0 or value > 2:
            return None
        return value


class ChatAttachment(BaseModel):
    """图片附件。data 为标准 base64，解码后单张不超过 2MiB。"""

    media_type: ImageMediaType = Field(alias="mediaType")
    data: str

    model_config = {"populate_by_name": True}

    @field_validator("data")
    @classmethod
    def validate_base64_payload(cls, value: str) -> str:
        if _decoded_attachment_size(value) > MAX_ATTACHMENT_BYTES:
            raise ValueError("attachment exceeds 2MiB")
        return value


def _decoded_attachment_size(value: str) -> int:
    try:
        decoded = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("attachment data is not valid base64") from exc
    return len(decoded)


def limit_attachment_list(value: list[ChatAttachment] | None) -> list[ChatAttachment] | None:
    if not value:
        return None
    total = sum(_decoded_attachment_size(item.data) for item in value)
    if total > MAX_ATTACHMENTS_TOTAL_BYTES:
        raise ValueError("attachments exceed 5MiB")
    return value


class ChatHistoryMessage(BaseModel):
    """多轮历史消息；由 Java 从会话真相源加载后随请求传入（Python stateless）。"""

    role: Literal["USER", "ASSISTANT"]
    content: str
    attachments: list[ChatAttachment] | None = Field(default=None, max_length=MAX_ATTACHMENTS)

    @field_validator("content")
    @classmethod
    def cap_stored_content(cls, value: str) -> str:
        # 已落库的回复可能长于本轮用户消息上限。截断后仍走 trim_history，避免整轮 400 把会话卡死。
        if len(value) <= MAX_MESSAGE_CHARS:
            return value
        return value[:MAX_MESSAGE_CHARS]

    @field_validator("attachments")
    @classmethod
    def cap_history_attachments(
        cls, value: list[ChatAttachment] | None
    ) -> list[ChatAttachment] | None:
        return limit_attachment_list(value)


class ChatDocument(BaseModel):
    """本轮上传文件的已抽取文本（抽取在 Java 站点层完成，原文不入 Python）。

    仅作用于本轮：不进历史回放、不落库。text 超上限由 Java 先行截断并标 truncated，
    这里再以字段上限兜底，防止 internal 调用方失误撑爆上下文。
    """

    name: str = Field(min_length=1, max_length=MAX_DOCUMENT_NAME_CHARS)
    text: str = Field(max_length=MAX_DOCUMENT_TEXT_CHARS)
    truncated: bool = False


class EmbeddingClassCredentials(BaseModel):
    """登记的一类嵌入模型。分类键是 embeddingSpec，不是 api key。"""

    embedding_spec: str = Field(alias="embeddingSpec", min_length=1, max_length=256)
    api_key: str | None = Field(default=None, alias="apiKey", max_length=4096)
    base_url: str | None = Field(default=None, alias="baseUrl", max_length=512)

    model_config = {"populate_by_name": True}


class ModelSelection(BaseModel):
    """Java 模型入口带入的本次凭证。缺失时回退进程 AI_MODEL。"""

    spec: str = Field(min_length=1, max_length=256)
    api_key: str = Field(alias="apiKey", min_length=1, max_length=4096)
    base_url: str | None = Field(default=None, alias="baseUrl", max_length=512)
    embedding_spec: str | None = Field(default=None, alias="embeddingSpec", max_length=256)

    model_config = {"populate_by_name": True}


class ChatRequest(BaseModel):
    agent_id: str = Field(alias="agentId")
    message: str = Field(max_length=MAX_MESSAGE_CHARS)
    attachments: list[ChatAttachment] | None = Field(default=None, max_length=MAX_ATTACHMENTS)
    documents: list[ChatDocument] | None = Field(default=None, max_length=MAX_DOCUMENTS)
    locale: str | None = None
    # 客户端只传 IANA 时区名。缺失或非法由组装段回退 UTC，这里不拒请求。
    timezone: str | None = None
    conversation_id: str | None = Field(default=None, alias="conversationId")
    prompt_profile: PromptProfile | None = Field(default=None, alias="promptProfile")
    history: list[ChatHistoryMessage] | None = Field(default=None, max_length=MAX_HISTORY_ITEMS)
    skills: list[SkillId] | None = Field(default=None, max_length=MAX_SKILL_IDS)
    todos: list[TodoItem] | None = Field(default=None, max_length=MAX_TODO_ITEMS)
    model_selection: ModelSelection | None = Field(default=None, alias="model")
    # 入口备援链（各自带凭证）；主用构建失败时不得顶替，仅运行期 API 错误时顺链切换
    fallback_models: list[ModelSelection] = Field(
        default_factory=list, alias="fallbackModels", max_length=8
    )
    business_api_base_url: str | None = Field(default=None, alias="businessApiBaseUrl", max_length=512)
    knowledge_context: list[str] | None = Field(default=None, alias="knowledgeContext", max_length=4)
    memories: list[str] | None = Field(default=None, max_length=MAX_MEMORIES)
    memory_enabled: bool = Field(default=False, alias="memoryEnabled")
    # 向量索引凭证，与回答模型分开。两种对话模式共用；都空时用进程嵌入模型。
    embedding_spec: str | None = Field(default=None, alias="embeddingSpec", max_length=256)
    embedding_api_key: str | None = Field(default=None, alias="embeddingApiKey", max_length=4096)
    embedding_base_url: str | None = Field(default=None, alias="embeddingBaseUrl", max_length=512)
    # 省略时仍只用上面这一套。登记多类时检索按名次合并，上限 4。
    embedding_classes: list[EmbeddingClassCredentials] | None = Field(
        default=None, alias="embeddingClasses"
    )

    model_config = {"populate_by_name": True}

    @field_validator("attachments")
    @classmethod
    def cap_request_attachments(
        cls, value: list[ChatAttachment] | None
    ) -> list[ChatAttachment] | None:
        return limit_attachment_list(value)

    @field_validator("embedding_classes")
    @classmethod
    def cap_embedding_classes(
        cls, value: list[EmbeddingClassCredentials] | None
    ) -> list[EmbeddingClassCredentials] | None:
        return limit_embedding_class_specs(value)

    @field_validator("knowledge_context")
    @classmethod
    def cap_knowledge_snippets(cls, value: list[str] | None) -> list[str] | None:
        if not value:
            return value
        snippets: list[str] = []
        for item in value:
            text = item.strip()
            if text:
                snippets.append(text[:2000])
        return snippets or None

    @field_validator("memories")
    @classmethod
    def cap_memories(cls, value: list[str] | None) -> list[str] | None:
        if not value:
            return None
        kept: list[str] = []
        for item in value:
            text = item.strip()
            if text:
                kept.append(text[:MAX_MEMORY_CHARS])
        return kept or None

    @field_validator("todos")
    @classmethod
    def reject_duplicate_todo_ids(cls, value: list[TodoItem] | None) -> list[TodoItem] | None:
        if not value:
            return value
        seen: set[str] = set()
        for item in value:
            if item.id in seen:
                raise ValueError(f"duplicate todo id: {item.id}")
            seen.add(item.id)
        return value


class ChatUsage(BaseModel):
    """单轮用量（§22.4）；token 数由 Provider 回报，未回报时为 0。"""

    requests: int = 0
    tool_calls: int = Field(default=0, alias="toolCalls")
    input_tokens: int = Field(default=0, alias="inputTokens")
    output_tokens: int = Field(default=0, alias="outputTokens")

    model_config = {"populate_by_name": True}


class ToolTraceItem(BaseModel):
    """工具调用轨迹（§22.8）：只有工具名与成败，不含参数/返回内容。"""

    name: str
    ok: bool


class CitationLocation(BaseModel):
    """页码或表格行区间。缺的字段不写出。"""

    page: int | None = None
    sheet: str | None = None
    row_start: int | None = Field(default=None, alias="rowStart")
    row_end: int | None = Field(default=None, alias="rowEnd")

    model_config = {"populate_by_name": True}

    @model_serializer(mode="wrap")
    def _drop_nulls(self, handler):  # type: ignore[no-untyped-def]
        data = handler(self)
        if isinstance(data, dict):
            return {key: value for key, value in data.items() if value is not None}
        return data


class ChatCitation(BaseModel):
    """本轮 grounding 实际采用的来源。无采用时响应里不出现该字段。

    cite 是 collector 顺序编号。title / location 只在索引 metadata 里有时出现。
    """

    cite: int | None = None
    source_id: str = Field(alias="sourceId")
    chunk_index: int = Field(alias="chunkIndex")
    score: float
    title: str | None = None
    location: CitationLocation | None = None

    model_config = {"populate_by_name": True}

    @model_serializer(mode="wrap")
    def _drop_nulls(self, handler):  # type: ignore[no-untyped-def]
        data = handler(self)
        if isinstance(data, dict):
            return {key: value for key, value in data.items() if value is not None}
        return data


class SearchSummaryItem(BaseModel):
    """一次知识搜索的安全摘要：查询词、命中数与来源标识，不含片段正文。"""

    query: str
    hit_count: int = Field(alias="hitCount")
    sources: list[ChatCitation] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class ChatSuccessData(BaseModel):
    reply: str
    conversation_id: str | None = Field(default=None, alias="conversationId")
    provider_status: str = Field(alias="providerStatus")
    usage: ChatUsage = Field(default_factory=ChatUsage)
    tools: list[ToolTraceItem] = Field(default_factory=list)
    skills_applied: list[str] = Field(default_factory=list, alias="skillsApplied")
    skills_missing: list[str] | None = Field(default=None, alias="skillsMissing")
    todos: list[TodoItem] | None = None
    retrieval_degraded: bool = Field(default=False, alias="retrievalDegraded")
    citations: list[ChatCitation] | None = None
    searches: list[SearchSummaryItem] | None = None
    extracted_memories: list[str] = Field(default_factory=list, alias="extractedMemories")
    # 实际产生回复的模型名（Provider 回报）；备援切换后与请求 spec 不同，Java 据此记 actual
    model: str | None = None

    model_config = {"populate_by_name": True}


class ChatSuccessResponse(BaseModel):
    ok: bool = True
    data: ChatSuccessData


class ErrorResponse(BaseModel):
    ok: bool = False
    error_code: str = Field(alias="errorCode")
    message: str
    provider_status: str | None = Field(default=None, alias="providerStatus")

    model_config = {"populate_by_name": True}
