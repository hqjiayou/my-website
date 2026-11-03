"""POST /internal/v1/experience/extract 契约：经验提炼（Issue #184 UoW-2）。

请求与 ocr 同一形状携带模型入口链（model + fallbackModels，各自带凭证），
另加候选对话轮（turns）与已有条目标题（existingTitles，去重用）。
每轮带 turnId（Java 侧不透明引用，候选原样回带以便落来源 id）。
响应回结构化候选数组、单次调用用量与命中的入口 spec。
"""

from pydantic import BaseModel, Field

from agent_services.schemas.chat import ChatUsage, ModelSelection

MAX_TURNS = 20
MAX_EXISTING_TITLES = 200
MAX_TURN_ID_CHARS = 64
MAX_USER_MESSAGE_CHARS = 8000
MAX_ASSISTANT_REPLY_CHARS = 12000
MAX_META_SUMMARY_CHARS = 2000
MAX_TITLE_CHARS = 256


class ExperienceTurn(BaseModel):
    turn_id: str = Field(alias="turnId", min_length=1, max_length=MAX_TURN_ID_CHARS)
    user_message: str = Field(alias="userMessage", max_length=MAX_USER_MESSAGE_CHARS)
    assistant_reply: str = Field(alias="assistantReply", max_length=MAX_ASSISTANT_REPLY_CHARS)
    meta_summary: str | None = Field(default=None, alias="metaSummary", max_length=MAX_META_SUMMARY_CHARS)

    model_config = {"populate_by_name": True}


class ExperienceExtractRequest(BaseModel):
    turns: list[ExperienceTurn] = Field(min_length=1, max_length=MAX_TURNS)
    existing_titles: list[str] = Field(
        default_factory=list, alias="existingTitles", max_length=MAX_EXISTING_TITLES
    )
    model_selection: ModelSelection | None = Field(default=None, alias="model")
    fallback_models: list[ModelSelection] = Field(
        default_factory=list, alias="fallbackModels", max_length=8
    )

    model_config = {"populate_by_name": True}


class ExperienceCandidate(BaseModel):
    source_turn_id: str = Field(alias="sourceTurnId")
    title: str = Field(max_length=MAX_TITLE_CHARS)
    symptom: str | None = None
    lesson: str
    evidence: str | None = None

    model_config = {"populate_by_name": True}


class ExperienceExtractResponse(BaseModel):
    candidates: list[ExperienceCandidate]
    usage: ChatUsage
    model_spec: str = Field(alias="modelSpec")

    model_config = {"populate_by_name": True}
