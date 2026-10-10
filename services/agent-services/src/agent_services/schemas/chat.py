import math
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints, field_validator

from agent_services.schemas.todo import MAX_TODO_ITEMS, TodoItem

# 纵深防御上限（§21.4）：Java DTO 先行校验，这里防止 internal 调用方失误撑爆上下文
MAX_MESSAGE_CHARS = 16000
MAX_HISTORY_ITEMS = 50
MAX_SKILL_IDS = 16
MAX_SKILL_ID_LENGTH = 64

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


class ChatHistoryMessage(BaseModel):
    """多轮历史消息；由 Java 从会话真相源加载后随请求传入（Python stateless）。"""

    role: Literal["USER", "ASSISTANT"]
    content: str

    @field_validator("content")
    @classmethod
    def cap_stored_content(cls, value: str) -> str:
        # 已落库的回复可能长于本轮用户消息上限。截断后仍走 trim_history，避免整轮 400 把会话卡死。
        if len(value) <= MAX_MESSAGE_CHARS:
            return value
        return value[:MAX_MESSAGE_CHARS]


class ChatRequest(BaseModel):
    site_id: str = Field(alias="siteId")
    message: str = Field(max_length=MAX_MESSAGE_CHARS)
    locale: str | None = None
    conversation_id: str | None = Field(default=None, alias="conversationId")
    prompt_profile: PromptProfile | None = Field(default=None, alias="promptProfile")
    history: list[ChatHistoryMessage] | None = Field(default=None, max_length=MAX_HISTORY_ITEMS)
    skills: list[SkillId] | None = Field(default=None, max_length=MAX_SKILL_IDS)
    todos: list[TodoItem] | None = Field(default=None, max_length=MAX_TODO_ITEMS)

    model_config = {"populate_by_name": True}


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


class ChatSuccessData(BaseModel):
    reply: str
    conversation_id: str | None = Field(default=None, alias="conversationId")
    provider_status: str = Field(alias="providerStatus")
    usage: ChatUsage = Field(default_factory=ChatUsage)
    tools: list[ToolTraceItem] = Field(default_factory=list)
    skills_applied: list[str] = Field(default_factory=list, alias="skillsApplied")
    skills_missing: list[str] | None = Field(default=None, alias="skillsMissing")
    todos: list[TodoItem] | None = None

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
