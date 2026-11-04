"""Issue #188：后台委派——派发不阻塞、状态机、取回/取消、并发与 TTL、工具面隔离。

工具层直调为主（四工具是 tool_plain、无 ctx），另有两条 run_chat 端到端：
派发后主 run 照常收尾、跨 run（新 toolset 实例、同一注册表）取回结果。
等待后台完成一律短轮询注册表状态，不靠长 sleep 凑时序；需要「还在跑」的
场景用 asyncio.Event 把研究模型卡住，确定性地保持 RUNNING。
"""

import asyncio
import time

from conftest import make_settings
from pydantic_ai.messages import (
    ModelMessage,
    ModelResponse,
    TextPart,
    ToolCallPart,
    ToolReturnPart,
)
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.background_tasks import (
    STATUS_CANCELLED,
    STATUS_FAILED,
    STATUS_RUNNING,
    STATUS_SUCCEEDED,
    STATUS_TIMED_OUT,
    TERMINAL_STATUSES,
    BackgroundTaskRegistry,
    build_background_toolset,
)
from agent_services.agent.harness import assemble_harness
from agent_services.agent.runtime import ChatReply, RunBudget, run_chat
from agent_services.agent.subagent import DELEGATE_RESULT_MAX_CHARS
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.retrieval.store import RetrievedChunk


class _FakeRetrieval:
    def __init__(self, content: str = "BG-FACT") -> None:
        self.calls: list[tuple[str, str]] = []
        self.degraded = False
        self._content = content

    async def retrieve_chunks(
        self, *, agent_id: str, query: str, **_kwargs: object
    ) -> list[RetrievedChunk]:
        self.calls.append((agent_id, query))
        return [
            RetrievedChunk(
                source_id="kb:1", chunk_index=0, content=self._content, score=0.9
            )
        ]


def _tool(toolset: object, name: str):  # noqa: ANN202 — 取 tool_plain 的底层函数直调
    return toolset.tools[name].function  # type: ignore[attr-defined]


async def _wait_terminal(
    registry: BackgroundTaskRegistry, task_id: str, timeout: float = 5.0
):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        record = registry.get(task_id)
        assert record is not None, f"task {task_id} missing from registry"
        if record.status in TERMINAL_STATUSES:
            return record
        await asyncio.sleep(0.01)
    raise AssertionError(
        f"task {task_id} did not reach a terminal status in {timeout}s"
    )


async def _wait_status(
    registry: BackgroundTaskRegistry, task_id: str, status: str, timeout: float = 5.0
):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        record = registry.get(task_id)
        assert record is not None
        if record.status == status:
            return record
        await asyncio.sleep(0.01)
    raise AssertionError(f"task {task_id} did not reach {status} in {timeout}s")


def _text_model(text: str) -> FunctionModel:
    return FunctionModel(lambda _m, _i: ModelResponse(parts=[TextPart(text)]))


async def test_spawn_returns_immediately_then_result_retrievable() -> None:
    gate = asyncio.Event()
    seen_tool_names: list[set[str]] = []

    async def respond(_messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        seen_tool_names.append({tool.name for tool in info.function_tools})
        await gate.wait()
        return ModelResponse(parts=[TextPart("BG-CONCLUSION")])

    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        FunctionModel(respond), agent_id="7", retrieval=None, registry=registry
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="research the thing")
    assert spawned["status"] in ("QUEUED", STATUS_RUNNING)
    task_id = spawned["task_id"]
    assert isinstance(task_id, str) and task_id

    # 研究还卡在 gate 上：spawn 已返回、任务未结束，get 如实报未完成、无 result。
    await _wait_status(registry, task_id, STATUS_RUNNING)
    pending = await _tool(toolset, "get_background_task")(task_id=task_id)
    assert pending["status"] == STATUS_RUNNING
    assert "result" not in pending

    gate.set()
    record = await _wait_terminal(registry, task_id)
    assert record.status == STATUS_SUCCEEDED

    fetched = await _tool(toolset, "get_background_task")(task_id=task_id)
    assert fetched["status"] == STATUS_SUCCEEDED
    assert fetched["result"] == "BG-CONCLUSION"
    usage = fetched["usage"]
    assert isinstance(usage, dict) and usage["requests"] >= 1
    # 子 agent 工具面：只有知识搜索，没有委派/后台工具（深度恒 1）。
    assert seen_tool_names and all(
        names == {"search_site_knowledge"} for names in seen_tool_names
    )


async def test_failed_subtask_becomes_failed_status_with_error() -> None:
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        raise RuntimeError("boom")

    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        FunctionModel(respond), agent_id="7", retrieval=None, registry=registry
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="will fail")
    record = await _wait_terminal(registry, spawned["task_id"])
    assert record.status == STATUS_FAILED
    fetched = await _tool(toolset, "get_background_task")(task_id=spawned["task_id"])
    assert fetched["status"] == STATUS_FAILED
    assert str(fetched["error"]).startswith("Background task failed:")
    assert "result" not in fetched


async def test_usage_limit_marks_failed() -> None:
    retrieval = _FakeRetrieval()

    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        # 每轮都再搜一次：请求上限 1 时第二轮必然超限。
        return ModelResponse(
            parts=[
                ToolCallPart(tool_name="search_site_knowledge", args={"query": "again"})
            ]
        )

    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        FunctionModel(respond),
        agent_id="7",
        retrieval=retrieval,  # type: ignore[arg-type]
        registry=registry,
        max_requests=1,
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="loop forever")
    record = await _wait_terminal(registry, spawned["task_id"])
    assert record.status == STATUS_FAILED
    assert "exceeded the run budget" in str(record.error)


async def test_timeout_marks_timed_out() -> None:
    async def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        await asyncio.sleep(2)
        return ModelResponse(parts=[TextPart("too late")])

    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        FunctionModel(respond),
        agent_id="7",
        retrieval=None,
        registry=registry,
        timeout_seconds=0.2,
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="slow research")
    record = await _wait_terminal(registry, spawned["task_id"])
    assert record.status == STATUS_TIMED_OUT
    assert "timed out after 0.2 seconds" in str(record.error)


async def test_cancel_running_task_then_terminal() -> None:
    gate = asyncio.Event()

    async def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        await gate.wait()
        return ModelResponse(parts=[TextPart("never seen")])

    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        FunctionModel(respond), agent_id="7", retrieval=None, registry=registry
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="to be cancelled")
    task_id = spawned["task_id"]
    await _wait_status(registry, task_id, STATUS_RUNNING)

    cancelled = await _tool(toolset, "cancel_background_task")(task_id=task_id)
    assert cancelled == {"task_id": task_id, "status": STATUS_CANCELLED}
    record = await _wait_terminal(registry, task_id)
    assert record.status == STATUS_CANCELLED
    fetched = await _tool(toolset, "get_background_task")(task_id=task_id)
    assert fetched["status"] == STATUS_CANCELLED
    assert "result" not in fetched

    again = await _tool(toolset, "cancel_background_task")(task_id=task_id)
    assert "already CANCELLED" in str(again["error"])
    gate.set()


async def test_cancel_finished_task_is_rejected() -> None:
    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        _text_model("done"), agent_id="7", retrieval=None, registry=registry
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="quick")
    await _wait_terminal(registry, spawned["task_id"])
    rejected = await _tool(toolset, "cancel_background_task")(
        task_id=spawned["task_id"]
    )
    assert rejected["status"] == STATUS_SUCCEEDED
    assert "already SUCCEEDED" in str(rejected["error"])


async def test_concurrency_limit_rejects_until_a_slot_frees() -> None:
    gate = asyncio.Event()

    async def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        await gate.wait()
        return ModelResponse(parts=[TextPart("released")])

    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        FunctionModel(respond),
        agent_id="7",
        retrieval=None,
        registry=registry,
        max_concurrent=2,
    )
    first = await _tool(toolset, "spawn_background_task")(task="one")
    second = await _tool(toolset, "spawn_background_task")(task="two")
    assert "task_id" in first and "task_id" in second
    third = await _tool(toolset, "spawn_background_task")(task="three")
    assert "task_id" not in third
    assert "limit reached" in str(third["error"]) and "2" in str(third["error"])

    gate.set()
    await _wait_terminal(registry, first["task_id"])  # type: ignore[arg-type]
    await _wait_terminal(registry, second["task_id"])  # type: ignore[arg-type]
    fourth = await _tool(toolset, "spawn_background_task")(task="four")
    assert "task_id" in fourth
    await _wait_terminal(registry, fourth["task_id"])  # type: ignore[arg-type]


async def test_expired_result_reports_not_found() -> None:
    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        _text_model("short-lived"), agent_id="7", retrieval=None, registry=registry
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="expires")
    record = await _wait_terminal(registry, spawned["task_id"])
    assert record.status == STATUS_SUCCEEDED
    # 把结束时间拨到 TTL 之外，模拟结果保留期已过（不真等一小时）。
    assert record.finished_at is not None
    record.finished_at -= 3600

    fetched = await _tool(toolset, "get_background_task")(task_id=spawned["task_id"])
    assert "not found" in str(fetched["error"]) and "expired" in str(fetched["error"])
    listed = await _tool(toolset, "list_background_tasks")()
    assert listed == {"tasks": [], "count": 0}


async def test_result_truncation_matches_delegate_cap() -> None:
    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        _text_model("x" * (DELEGATE_RESULT_MAX_CHARS + 500)),
        agent_id="7",
        retrieval=None,
        registry=registry,
    )
    spawned = await _tool(toolset, "spawn_background_task")(task="verbose")
    await _wait_terminal(registry, spawned["task_id"])
    fetched = await _tool(toolset, "get_background_task")(task_id=spawned["task_id"])
    result = fetched["result"]
    assert isinstance(result, str) and len(result) == DELEGATE_RESULT_MAX_CHARS
    assert result.endswith("[truncated]")


async def test_tool_input_validation_and_unknown_ids() -> None:
    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        _text_model("x"), agent_id="7", retrieval=None, registry=registry
    )
    assert await _tool(toolset, "spawn_background_task")(task="   ") == {
        "error": "task is required"
    }
    missing_get = await _tool(toolset, "get_background_task")(task_id="nope")
    assert "not found" in str(missing_get["error"])
    missing_cancel = await _tool(toolset, "cancel_background_task")(task_id="nope")
    assert "not found" in str(missing_cancel["error"])


async def test_list_is_newest_first_and_registry_limit() -> None:
    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        _text_model("ok"), agent_id="7", retrieval=None, registry=registry
    )
    ids: list[str] = []
    for label in ("first", "second", "third"):
        spawned = await _tool(toolset, "spawn_background_task")(task=label)
        ids.append(spawned["task_id"])  # type: ignore[arg-type]
        await _wait_terminal(registry, spawned["task_id"])  # type: ignore[arg-type]
    listed = await _tool(toolset, "list_background_tasks")()
    assert listed["count"] == 3
    tasks = listed["tasks"]
    assert isinstance(tasks, list)
    assert [item["task_id"] for item in tasks] == list(reversed(ids))
    assert [item["task"] for item in tasks] == ["third", "second", "first"]
    assert all(item["status"] == STATUS_SUCCEEDED for item in tasks)
    assert [record.id for record in registry.list_recent(limit=2)] == list(
        reversed(ids)
    )[:2]


def _is_research(info: AgentInfo) -> bool:
    names = {tool.name for tool in info.function_tools}
    return "search_site_knowledge" in names and "spawn_background_task" not in names


def _returns(messages: list[ModelMessage], name: str) -> list[ToolReturnPart]:
    found: list[ToolReturnPart] = []
    for message in messages:
        for part in message.parts:
            if isinstance(part, ToolReturnPart) and part.tool_name == name:
                found.append(part)
    return found


async def test_spawn_via_run_chat_does_not_block_parent() -> None:
    def respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        if _is_research(info):
            raise RuntimeError("research blew up")
        if not _returns(messages, "spawn_background_task"):
            return ModelResponse(
                parts=[
                    ToolCallPart(
                        tool_name="spawn_background_task",
                        args={"task": "check the archives"},
                    )
                ]
            )
        return ModelResponse(parts=[TextPart("started it; will report back")])

    model = FunctionModel(respond)
    registry = BackgroundTaskRegistry(ttl_seconds=60)
    toolset = build_background_toolset(
        model, agent_id="3", retrieval=None, registry=registry
    )
    outcome = await run_chat(
        model,
        agent_id="3",
        message="dig into this in the background",
        toolsets=[toolset],
        budget=RunBudget(),
    )
    assert isinstance(outcome, ChatReply)
    assert outcome.reply == "started it; will report back"
    assert outcome.tools == [{"name": "spawn_background_task", "ok": True}]
    records = registry.list_recent()
    assert len(records) == 1
    record = await _wait_terminal(registry, records[0].id)
    # 子任务失败只落状态，主 run 已正常收尾（上面的 ChatReply 即证据）。
    assert record.status == STATUS_FAILED


async def test_result_fetched_in_a_later_run_with_fresh_toolset() -> None:
    registry = BackgroundTaskRegistry(ttl_seconds=60)

    def research_respond(
        _messages: list[ModelMessage], info: AgentInfo
    ) -> ModelResponse:
        if _is_research(info):
            return ModelResponse(parts=[TextPart("LATER-FACT")])
        raise AssertionError("research model must not act as the parent here")

    research_model = FunctionModel(research_respond)
    first_toolset = build_background_toolset(
        research_model, agent_id="3", retrieval=None, registry=registry
    )
    spawned = await _tool(first_toolset, "spawn_background_task")(
        task="find LATER-FACT"
    )
    task_id = spawned["task_id"]
    await _wait_terminal(registry, task_id)  # type: ignore[arg-type]

    def parent_respond(messages: list[ModelMessage], info: AgentInfo) -> ModelResponse:
        if _is_research(info):
            return ModelResponse(parts=[TextPart("LATER-FACT")])
        if not _returns(messages, "get_background_task"):
            return ModelResponse(
                parts=[
                    ToolCallPart(
                        tool_name="get_background_task", args={"task_id": task_id}
                    )
                ]
            )
        return ModelResponse(parts=[TextPart("the background result was LATER-FACT")])

    parent_model = FunctionModel(parent_respond)
    # 后续轮次是全新构建的 toolset 实例，但注册表是同一个（进程级共享的语义）。
    second_toolset = build_background_toolset(
        parent_model, agent_id="3", retrieval=None, registry=registry
    )
    outcome = await run_chat(
        parent_model,
        agent_id="3",
        message="what did the background task find?",
        toolsets=[second_toolset],
        budget=RunBudget(),
    )
    assert isinstance(outcome, ChatReply)
    assert "LATER-FACT" in outcome.reply
    assert outcome.tools == [{"name": "get_background_task", "ok": True}]


_BACKGROUND_TOOL_NAMES = {
    "spawn_background_task",
    "get_background_task",
    "list_background_tasks",
    "cancel_background_task",
}


def _names(toolsets: list[object]) -> set[str]:
    names: set[str] = set()
    for toolset in toolsets:
        tools = getattr(toolset, "tools", None)
        if isinstance(tools, dict):
            for key, tool in tools.items():
                raw = getattr(tool, "name", None) or key
                if isinstance(raw, str):
                    names.add(raw)
    return names


def _factory_toolsets(**overrides: object) -> list[object]:
    model = FunctionModel(lambda _m, _i: ModelResponse(parts=[TextPart("x")]))
    return build_execution_toolsets(
        make_settings(
            agent_enable_todo_tools=True,
            business_api_base_url="",
            mcp_servers_json="",
            **overrides,  # type: ignore[arg-type]
        ),
        "9",
        todo_store=TodoStore(),
        model=model,  # type: ignore[arg-type]
    )


def test_factory_gating_both_switches() -> None:
    both_on = _names(
        _factory_toolsets(
            agent_subagent_enabled=True, agent_background_tasks_enabled=True
        )
    )
    assert "delegate_task" in both_on and _BACKGROUND_TOOL_NAMES <= both_on

    background_off = _names(
        _factory_toolsets(
            agent_subagent_enabled=True, agent_background_tasks_enabled=False
        )
    )
    assert "delegate_task" in background_off
    assert not (_BACKGROUND_TOOL_NAMES & background_off)

    subagent_off = _names(
        _factory_toolsets(
            agent_subagent_enabled=False, agent_background_tasks_enabled=True
        )
    )
    assert "delegate_task" not in subagent_off
    assert not (_BACKGROUND_TOOL_NAMES & subagent_off)


def test_harness_delegation_section_background_variants(tmp_path) -> None:  # noqa: ANN001
    (tmp_path / "background.md").write_text("BG", encoding="utf-8")

    with_background = assemble_harness(
        agent_id="9",
        message="hi",
        toolsets=_factory_toolsets(
            agent_subagent_enabled=True, agent_background_tasks_enabled=True
        ),
        harness_dir=str(tmp_path),
    )
    assert "# Delegation" in with_background.system_prompt
    assert "spawn_background_task" in with_background.system_prompt
    assert "task_id" in with_background.system_prompt
    assert "never guess whether it has finished" in with_background.system_prompt

    delegate_only = assemble_harness(
        agent_id="9",
        message="hi",
        toolsets=_factory_toolsets(
            agent_subagent_enabled=True, agent_background_tasks_enabled=False
        ),
        harness_dir=str(tmp_path),
    )
    legacy_block = (
        "# Delegation\n\n"
        "Use delegate_task for a self-contained sub-question, or when several independent angles "
        "can be checked in parallel.\n"
        "Do not delegate a simple question, or anything that needs a write or other business action.\n"
        "Each delegated task must stand alone; the sub-agent does not see this conversation."
    )
    # 只有同步委派时整段与旧版逐字一致，且不出现任何后台工具字样。
    assert legacy_block in delegate_only.system_prompt
    assert "spawn_background_task" not in delegate_only.system_prompt
    assert "background task" not in delegate_only.system_prompt

    neither = assemble_harness(
        agent_id="9",
        message="hi",
        toolsets=_factory_toolsets(
            agent_subagent_enabled=False, agent_background_tasks_enabled=True
        ),
        harness_dir=str(tmp_path),
    )
    assert "# Delegation" not in neither.system_prompt
    assert "spawn_background_task" not in neither.system_prompt
