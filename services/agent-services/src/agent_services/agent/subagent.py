"""把自包含子任务交给深度为 1 的研究子 agent。

子 agent 只有知识搜索，不能写业务、不能再委派。失败一律变成工具返回，主 run 不因此中断。
用量：子 run 用自己的 RunUsage 和请求上限；结束时并入主 run 的 ctx.usage。
主 run 的 UsageLimits 只检查这份 usage，所以下一步限额和最终上报都含有子 run。
并行委派在并入之前用「已用请求 + 已预留请求」判断还能不能开跑。
token 没有可靠的单次预估：已超过总 token 上限就拒绝；否则把当时剩余值设成子 run 的上限。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

import anyio
from pydantic_ai import Agent, RunContext
from pydantic_ai.exceptions import ToolFailed, UsageLimitExceeded
from pydantic_ai.models import Model
from pydantic_ai.toolsets import FunctionToolset
from pydantic_ai.usage import RunUsage, UsageLimits

from agent_services.agent.knowledge_tools import build_knowledge_toolset
from agent_services.api.errors import external_failure_message
from agent_services.retrieval.service import RetrievalService

logger = logging.getLogger(__name__)

DELEGATE_RESULT_MAX_CHARS = 4000
_TRUNCATED = "\n[truncated]"

_RESEARCH_INSTRUCTIONS = (
    "You only carry out the single delegated task. "
    "Prefer search_site_knowledge to check site facts before you answer. "
    "Reply with a short conclusion and the supporting points. "
    "Do not greet the user, and do not mention how you were invoked."
)


class _DelegateToolset(FunctionToolset):
    """自带墙钟超时。不再套主 run 的工具超时，避免 ModelRetry 拖垮本轮。"""

    skip_outer_tool_timeout = True


@dataclass(frozen=True)
class _Grant:
    requests: int
    total_tokens_limit: int | None
    tool_calls_limit: int | None


class _DelegationBudget:
    def __init__(self, *, max_per_run: int, max_requests: int) -> None:
        self._max_per_run = max_per_run
        self._max_requests = max_requests if max_requests > 0 else 1
        self._lock = anyio.Lock()
        self._started = 0
        self._reserved_requests = 0

    async def acquire(self, usage: RunUsage, limits: UsageLimits | None) -> _Grant | str:
        async with self._lock:
            if self._started >= self._max_per_run:
                return f"Delegation limit reached: at most {self._max_per_run} per run."
            request_limit = limits.request_limit if limits is not None else None
            used_requests = usage.requests + self._reserved_requests
            if request_limit is not None:
                remaining = request_limit - used_requests
                if remaining < 1:
                    return "Not enough request budget left to delegate this task."
                grant_requests = min(self._max_requests, remaining)
            else:
                grant_requests = self._max_requests
            total_tokens_limit = _remaining_tokens(usage, limits)
            if total_tokens_limit == 0:
                return "Not enough token budget left to delegate this task."
            tool_calls_limit = _remaining_tool_calls(usage, limits)
            self._started += 1
            self._reserved_requests += grant_requests
            return _Grant(grant_requests, total_tokens_limit, tool_calls_limit)

    async def settle(self, usage: RunUsage, sub_usage: RunUsage, reserved_requests: int) -> None:
        async with self._lock:
            usage.incr(sub_usage)
            self._reserved_requests -= reserved_requests


def build_research_agent(
    model: Model,
    *,
    site_id: str,
    retrieval: RetrievalService | None,
) -> Agent[None, str]:
    """只有知识搜索的子 agent。没有 delegate_task、业务工具、MCP 或 Todo。"""
    return Agent(model, system_prompt=_RESEARCH_INSTRUCTIONS, toolsets=[build_knowledge_toolset(site_id, retrieval)])


def build_delegate_tool(
    model: Model,
    *,
    site_id: str,
    retrieval: RetrievalService | None,
    max_per_run: int = 4,
    timeout_seconds: float = 60,
    max_requests: int = 6,
) -> FunctionToolset:
    """闭包持有本 run 的模型、检索、站点和委派预算。"""
    research = build_research_agent(model, site_id=site_id, retrieval=retrieval)
    budget = _DelegationBudget(max_per_run=max_per_run, max_requests=max_requests)
    toolset = _DelegateToolset()

    @toolset.tool
    async def delegate_task(ctx: RunContext[None], task: str) -> str:
        """Delegate one self-contained subtask to a research helper that can only search site knowledge.

        The helper does not see this conversation, cannot write business data, and cannot delegate.
        Pass every fact the helper needs. Independent subtasks may be delegated in parallel.

        Args:
            task: the subtask to research.
        """
        cleaned = task.strip()
        if not cleaned:
            raise ToolFailed("task is required.")
        acquired = await budget.acquire(ctx.usage, ctx.usage_limits)
        if isinstance(acquired, str):
            raise ToolFailed(acquired)
        sub_usage = RunUsage()
        try:
            try:
                with anyio.fail_after(timeout_seconds):
                    result = await research.run(
                        cleaned,
                        usage=sub_usage,
                        usage_limits=UsageLimits(
                            request_limit=acquired.requests,
                            total_tokens_limit=acquired.total_tokens_limit,
                            tool_calls_limit=acquired.tool_calls_limit,
                        ),
                    )
            except TimeoutError:
                raise ToolFailed(
                    f"Delegation timed out after {timeout_seconds:g} seconds."
                ) from None
            except UsageLimitExceeded:
                raise ToolFailed("Delegation stopped: the subtask exceeded the run budget.") from None
            except Exception as exc:  # noqa: BLE001 — 委派失败只回给模型，不让主 run 失败
                logger.warning("delegation failed", exc_info=exc)
                raise ToolFailed(external_failure_message("Delegation failed", exc)) from None
        finally:
            # 超时取消后仍要把已发生的用量并回去，并放开预留。
            with anyio.CancelScope(shield=True):
                await budget.settle(ctx.usage, sub_usage, acquired.requests)
        text = str(result.output).strip()
        if not text:
            raise ToolFailed("Delegation returned an empty result.")
        return _limit_result(text)

    return toolset


def _remaining_tokens(usage: RunUsage, limits: UsageLimits | None) -> int | None:
    if limits is None or limits.total_tokens_limit is None:
        return None
    remaining = limits.total_tokens_limit - usage.total_tokens
    if remaining < 1:
        return 0
    return remaining


def _remaining_tool_calls(usage: RunUsage, limits: UsageLimits | None) -> int | None:
    if limits is None or limits.tool_calls_limit is None:
        return None
    remaining = limits.tool_calls_limit - usage.tool_calls
    if remaining < 1:
        return 0
    return remaining


def _limit_result(text: str) -> str:
    if len(text) <= DELEGATE_RESULT_MAX_CHARS:
        return text
    return text[: DELEGATE_RESULT_MAX_CHARS - len(_TRUNCATED)] + _TRUNCATED
