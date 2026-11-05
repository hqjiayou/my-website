"""Issue #188：进程内后台委派（background agent v1）。

与同步 delegate_task 并列：spawn 立即返回 task_id、主 run 不阻塞，子任务在
进程内继续跑，后续轮次用 get/list 取回状态与结果。执行核与子 agent 构造都
复用 subagent.py（run_research_task / build_research_agent）——子 agent 仍是
知识搜索 only、深度 1，不能再委派、也不能再派后台任务。

v1 边界（写死，不是缺陷）：注册表只在进程内存里，进程重启全部丢失；子任务
用量记进任务记录、取回时随结果返回，**不并入**派发它的主 run 用量（主 run
可能早已结束），也不入 Java 用量表。子预算是固定口径：请求上限沿用
AGENT_SUBAGENT_MAX_REQUESTS，墙钟用 AGENT_BACKGROUND_TASK_TIMEOUT_SECONDS。
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime

from pydantic_ai.models import Model
from pydantic_ai.toolsets import FunctionToolset
from pydantic_ai.usage import RunUsage, UsageLimits

from agent_services.agent.subagent import (
    _limit_result,
    build_research_agent,
    run_research_task,
)
from agent_services.retrieval.embeddings import EmbeddingClient
from agent_services.retrieval.service import EmbeddingClass, RetrievalService

logger = logging.getLogger(__name__)

STATUS_QUEUED = "QUEUED"
STATUS_RUNNING = "RUNNING"
STATUS_SUCCEEDED = "SUCCEEDED"
STATUS_FAILED = "FAILED"
STATUS_TIMED_OUT = "TIMED_OUT"
STATUS_CANCELLED = "CANCELLED"

TERMINAL_STATUSES = frozenset(
    {STATUS_SUCCEEDED, STATUS_FAILED, STATUS_TIMED_OUT, STATUS_CANCELLED}
)
ACTIVE_STATUSES = frozenset({STATUS_QUEUED, STATUS_RUNNING})

# list_background_tasks 一次最多列这么多条，新近优先。
BACKGROUND_TASK_LIST_LIMIT = 20
# 列表里任务文本只给摘要，避免一屏刷满上下文。
_TASK_SUMMARY_MAX_CHARS = 120


@dataclass
class BackgroundTaskRecord:
    id: str
    task: str
    status: str = STATUS_QUEUED
    created_at: float = field(default_factory=time.time)
    started_at: float | None = None
    finished_at: float | None = None
    result: str | None = None
    error: str | None = None
    usage: dict[str, int] | None = None
    handle: asyncio.Task[None] | None = field(default=None, repr=False)


class BackgroundTaskRegistry:
    """进程内任务注册表。只在单个事件循环里使用，记录操作本身不 await，
    检查与写入之间不会被其他协程插入。已结束任务保留 ttl_seconds 后清理，
    QUEUED/RUNNING 不过期（它们有墙钟超时兜底，必然会结束）。
    """

    def __init__(self, *, ttl_seconds: float = 3600) -> None:
        self._ttl_seconds = ttl_seconds if ttl_seconds > 0 else 3600
        self._records: dict[str, BackgroundTaskRecord] = {}

    def purge_expired(self, now: float | None = None) -> None:
        moment = time.time() if now is None else now
        expired = [
            task_id
            for task_id, record in self._records.items()
            if record.finished_at is not None
            and record.finished_at + self._ttl_seconds <= moment
        ]
        for task_id in expired:
            del self._records[task_id]

    def active_count(self) -> int:
        return sum(
            1 for record in self._records.values() if record.status in ACTIVE_STATUSES
        )

    def add(self, record: BackgroundTaskRecord) -> None:
        self._records[record.id] = record

    def get(self, task_id: str) -> BackgroundTaskRecord | None:
        self.purge_expired()
        return self._records.get(task_id)

    def list_recent(
        self, limit: int = BACKGROUND_TASK_LIST_LIMIT
    ) -> list[BackgroundTaskRecord]:
        self.purge_expired()
        ordered = sorted(
            self._records.values(), key=lambda record: record.created_at, reverse=True
        )
        return ordered[:limit]


_default_registry: BackgroundTaskRegistry | None = None


def default_background_registry(*, ttl_seconds: float = 3600) -> BackgroundTaskRegistry:
    """进程级默认注册表：跨请求共享，后台任务才能「本轮派发、后续轮次取回」。
    TTL 以第一次构建时的设置为准（设置来自进程环境，运行期不变）。"""
    global _default_registry
    if _default_registry is None:
        _default_registry = BackgroundTaskRegistry(ttl_seconds=ttl_seconds)
    return _default_registry


def _usage_summary(usage: RunUsage) -> dict[str, int]:
    return {
        "requests": usage.requests,
        "tool_calls": usage.tool_calls,
        "input_tokens": usage.input_tokens,
        "output_tokens": usage.output_tokens,
        "total_tokens": usage.total_tokens,
    }


def _iso(timestamp: float | None) -> str | None:
    if timestamp is None:
        return None
    return datetime.fromtimestamp(timestamp, tz=UTC).isoformat()


def _task_view(record: BackgroundTaskRecord) -> dict[str, object]:
    view: dict[str, object] = {
        "task_id": record.id,
        "status": record.status,
        "task": record.task,
        "created_at": _iso(record.created_at),
        "started_at": _iso(record.started_at),
        "finished_at": _iso(record.finished_at),
    }
    if record.status == STATUS_SUCCEEDED and record.result is not None:
        # 截断口径与同步委派一致：DELEGATE_RESULT_MAX_CHARS，见 subagent._limit_result。
        view["result"] = _limit_result(record.result)
    if record.error is not None:
        view["error"] = record.error
    if record.usage is not None:
        view["usage"] = record.usage
    return view


def _task_summary(record: BackgroundTaskRecord) -> dict[str, object]:
    task = record.task
    if len(task) > _TASK_SUMMARY_MAX_CHARS:
        task = task[:_TASK_SUMMARY_MAX_CHARS] + "…"
    return {
        "task_id": record.id,
        "status": record.status,
        "task": task,
        "created_at": _iso(record.created_at),
    }


def build_background_toolset(
    model: Model,
    *,
    agent_id: str,
    retrieval: RetrievalService | None,
    embedder: EmbeddingClient | None = None,
    embedding_model: str | None = None,
    embedding_classes: list[EmbeddingClass] | None = None,
    knowledge_base_ids: list[str] | None = None,
    registry: BackgroundTaskRegistry | None = None,
    max_concurrent: int = 2,
    timeout_seconds: float = 300,
    max_requests: int = 6,
    ttl_seconds: float = 3600,
) -> FunctionToolset:
    """四个后台任务工具。闭包持有研究子 agent 与注册表；注册表缺省用进程级
    单例，测试可注入独立实例。并发上限在 spawn 时数 QUEUED+RUNNING，超限
    直接回拒绝信息，不排队、不静默丢弃。"""
    research = build_research_agent(
        model,
        agent_id=agent_id,
        retrieval=retrieval,
        embedder=embedder,
        embedding_model=embedding_model,
        embedding_classes=embedding_classes,
        knowledge_base_ids=knowledge_base_ids,
    )
    store = (
        registry
        if registry is not None
        else default_background_registry(ttl_seconds=ttl_seconds)
    )
    concurrent_limit = max_concurrent if max_concurrent > 0 else 1
    request_limit = max_requests if max_requests > 0 else 1
    toolset = FunctionToolset()

    async def _run_record(record: BackgroundTaskRecord) -> None:
        record.status = STATUS_RUNNING
        record.started_at = time.time()
        usage = RunUsage()
        try:
            outcome = await run_research_task(
                research,
                record.task,
                timeout_seconds=timeout_seconds,
                usage_limits=UsageLimits(request_limit=request_limit),
                usage=usage,
                error_prefix="Background task failed",
            )
        except asyncio.CancelledError:
            # cancel_background_task 已先把状态置 CANCELLED；这里补齐结算后原样抛出，
            # 异常绝不逃逸成未捕获的 task 异常。
            record.status = STATUS_CANCELLED
            if record.finished_at is None:
                record.finished_at = time.time()
            record.usage = _usage_summary(usage)
            raise
        record.usage = _usage_summary(usage)
        record.finished_at = time.time()
        if outcome.kind == "ok":
            record.status = STATUS_SUCCEEDED
            record.result = outcome.text
        elif outcome.kind == "timeout":
            record.status = STATUS_TIMED_OUT
            record.error = (
                f"Background task timed out after {timeout_seconds:g} seconds."
            )
        elif outcome.kind == "usage_limit":
            record.status = STATUS_FAILED
            record.error = (
                "Background task stopped: the subtask exceeded the run budget."
            )
        elif outcome.kind == "empty":
            record.status = STATUS_FAILED
            record.error = "Background task returned an empty result."
        else:
            record.status = STATUS_FAILED
            record.error = outcome.error or "Background task failed."

    @toolset.tool_plain
    async def spawn_background_task(task: str) -> dict[str, object]:
        """Start a self-contained research subtask in the background and return immediately.

        The helper only searches site knowledge, does not see this conversation,
        and cannot delegate or start further background tasks. Use this when the
        subtask is slow or independent and the conversation should not wait for
        it; use delegate_task when the result is needed in this reply. Tell the
        user the returned task_id; the result can be fetched in a later turn
        with get_background_task.

        Args:
            task: the subtask to research, with every fact the helper needs.
        """
        cleaned = task.strip()
        if not cleaned:
            return {"error": "task is required"}
        store.purge_expired()
        if store.active_count() >= concurrent_limit:
            return {
                "error": (
                    f"background task limit reached: at most {concurrent_limit} tasks may be "
                    "queued or running at once; wait for one to finish or cancel one first"
                )
            }
        record = BackgroundTaskRecord(id=str(uuid.uuid4()), task=cleaned)
        store.add(record)
        record.handle = asyncio.create_task(_run_record(record))
        return {"task_id": record.id, "status": record.status, "task": record.task}

    @toolset.tool_plain
    async def get_background_task(task_id: str) -> dict[str, object]:
        """Check one background task and fetch its result when it has finished.

        Returns the status; a SUCCEEDED task includes its result and a usage
        summary, a FAILED or TIMED_OUT task includes the error. Results are
        kept for a limited time after the task finishes; an unknown or expired
        id returns an error.

        Args:
            task_id: the id returned by spawn_background_task or list_background_tasks.
        """
        record = store.get(task_id.strip())
        if record is None:
            return {
                "error": (
                    f"background task not found: {task_id} "
                    "(unknown id, or its result has expired)"
                )
            }
        return _task_view(record)

    @toolset.tool_plain
    async def list_background_tasks() -> dict[str, object]:
        """List recent background tasks, newest first: id, status, creation time, task summary.

        Use this to find a task id again in a later turn before calling
        get_background_task.
        """
        records = store.list_recent()
        return {
            "tasks": [_task_summary(record) for record in records],
            "count": len(records),
        }

    @toolset.tool_plain
    async def cancel_background_task(task_id: str) -> dict[str, object]:
        """Cancel a background task that has not finished yet.

        Tasks that already finished keep their final status and cannot be
        cancelled.

        Args:
            task_id: the id returned by spawn_background_task or list_background_tasks.
        """
        record = store.get(task_id.strip())
        if record is None:
            return {
                "error": (
                    f"background task not found: {task_id} "
                    "(unknown id, or its result has expired)"
                )
            }
        if record.status in TERMINAL_STATUSES:
            return {
                "error": (
                    f"background task {record.id} is already {record.status} "
                    "and cannot be cancelled"
                ),
                "task_id": record.id,
                "status": record.status,
            }
        record.status = STATUS_CANCELLED
        record.finished_at = time.time()
        if record.handle is not None:
            record.handle.cancel()
        return {"task_id": record.id, "status": STATUS_CANCELLED}

    return toolset
