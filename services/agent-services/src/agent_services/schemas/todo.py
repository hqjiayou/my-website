from typing import Literal

from pydantic import BaseModel, Field

TodoStatus = Literal["PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"]


class TodoItem(BaseModel):
    id: str
    title: str
    status: TodoStatus = "PENDING"


class TodoSnapshot(BaseModel):
    items: list[TodoItem] = Field(default_factory=list)

    def to_api(self) -> list[dict[str, str]]:
        return [item.model_dump() for item in self.items]
