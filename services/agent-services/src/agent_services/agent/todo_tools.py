"""Phase 5：Todo 编排 — 单次 agent run 内的任务列表（跨请求状态由 Java 传入/回收）。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field

from pydantic_ai.toolsets.function import FunctionToolset

from agent_services.schemas.todo import TodoItem, TodoSnapshot, TodoStatus


@dataclass
class TodoStore:
    items: list[TodoItem] = field(default_factory=list)

    def snapshot(self) -> TodoSnapshot:
        return TodoSnapshot(items=list(self.items))

    def _find(self, todo_id: str) -> TodoItem | None:
        for item in self.items:
            if item.id == todo_id:
                return item
        return None


def build_todo_toolset(store: TodoStore) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain
    def list_todos() -> dict[str, object]:
        """List current todos for this run."""
        return {"todos": store.snapshot().to_api()}

    @toolset.tool_plain
    def add_todo(title: str) -> dict[str, object]:
        """Add a new pending todo item."""
        cleaned = title.strip()
        if not cleaned:
            return {"error": "title is required"}
        item = TodoItem(id=str(uuid.uuid4()), title=cleaned, status="PENDING")
        store.items.append(item)
        return {"todo": item.model_dump(), "todos": store.snapshot().to_api()}

    @toolset.tool_plain
    def update_todo_status(todo_id: str, status: TodoStatus) -> dict[str, object]:
        """Update a todo status (PENDING | IN_PROGRESS | COMPLETED | CANCELLED)."""
        item = store._find(todo_id.strip())
        if item is None:
            return {"error": f"todo not found: {todo_id}"}
        if status == "IN_PROGRESS":
            for other in store.items:
                if other.id != item.id and other.status == "IN_PROGRESS":
                    other.status = "PENDING"
        item.status = status
        return {"todo": item.model_dump(), "todos": store.snapshot().to_api()}

    @toolset.tool_plain
    def complete_todo(todo_id: str) -> dict[str, object]:
        """Mark a todo as COMPLETED."""
        return update_todo_status(todo_id, "COMPLETED")

    return toolset


def todo_store_from_request(initial: list[TodoItem] | None) -> TodoStore:
    if not initial:
        return TodoStore()
    return TodoStore(items=[item.model_copy() for item in initial])
