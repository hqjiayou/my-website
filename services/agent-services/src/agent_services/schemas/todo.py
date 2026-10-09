from typing import Literal

from pydantic import BaseModel, Field

TodoStatus = Literal["PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"]

TERMINAL_TODO_STATUSES: frozenset[str] = frozenset({"COMPLETED", "CANCELLED"})
MAX_TODO_ITEMS = 20
MAX_TODO_ID_LENGTH = 64
MAX_TODO_TITLE_LENGTH = 200


class TodoItem(BaseModel):
    id: str = Field(min_length=1, max_length=MAX_TODO_ID_LENGTH)
    title: str = Field(min_length=1, max_length=MAX_TODO_TITLE_LENGTH)
    status: TodoStatus = "PENDING"


class TodoSnapshot(BaseModel):
    items: list[TodoItem] = Field(default_factory=list)

    def to_api(self) -> list[dict[str, str]]:
        return [item.model_dump() for item in self.items]
