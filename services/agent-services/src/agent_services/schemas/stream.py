"""应用层 SSE 事件契约（AgentStreamEvent）。

禁止透传 Pydantic AI 原始 stream event；Java/前端只认识本模块定义的事件类型。
"""

from enum import StrEnum
from typing import Any

from pydantic import BaseModel, Field


class AgentStreamEventType(StrEnum):
    START = "START"
    TEXT_DELTA = "TEXT_DELTA"
    THINKING = "THINKING"
    TOOL_START = "TOOL_START"
    TOOL_RESULT = "TOOL_RESULT"
    DONE = "DONE"
    ERROR = "ERROR"


class AgentStreamEvent(BaseModel):
    type: AgentStreamEventType
    data: dict[str, Any] = Field(default_factory=dict)

    def to_sse(self) -> str:
        import json

        return f"event: {self.type.value}\ndata: {json.dumps(self.data, ensure_ascii=False)}\n\n"
