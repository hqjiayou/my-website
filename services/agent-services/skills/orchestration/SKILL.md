---
name: orchestration
description: Multi-step orchestration via todo tools
version: "1"
---

# Orchestration (multi-step tasks)

Use the todo tools when the user request needs more than one step.

1. Call `list_todos` before planning.
2. Call `add_todo` for each concrete step (short titles).
3. Mark exactly one item `IN_PROGRESS` with `update_todo_status` while working on it.
4. Call `complete_todo` when a step is finished.
5. Use available Function Tools or MCP tools between todo updates when needed.

Keep the todo list small (≤ 8 items). Do not invent completed work — update todos honestly.
