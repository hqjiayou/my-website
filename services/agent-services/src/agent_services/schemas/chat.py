from typing import Literal

from pydantic import BaseModel, Field

from agent_services.schemas.todo import TodoItem


class PromptProfile(BaseModel):
    system_prompt: str | None = Field(default=None, alias="systemPrompt")
    temperature: float | None = None

    model_config = {"populate_by_name": True}


class ChatHistoryMessage(BaseModel):
    """多轮历史消息；由 Java 从会话真相源加载后随请求传入（Python stateless）。"""

    role: Literal["USER", "ASSISTANT"]
    content: str


class ChatRequest(BaseModel):
    site_id: str = Field(alias="siteId")
    message: str
    locale: str | None = None
    conversation_id: str | None = Field(default=None, alias="conversationId")
    prompt_profile: PromptProfile | None = Field(default=None, alias="promptProfile")
    history: list[ChatHistoryMessage] | None = None
    skills: list[str] | None = None
    todos: list[TodoItem] | None = None

    model_config = {"populate_by_name": True}


class ChatSuccessData(BaseModel):
    reply: str
    conversation_id: str | None = Field(default=None, alias="conversationId")
    provider_status: str = Field(alias="providerStatus")

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
