"""平台行为评测集（§22.10）：锁定 Agent 平台能力的可观察行为，不涉及业务。

与 test_evals_baseline 同样只用 FunctionModel（确定性、不触达真实 Provider）。
每个 Case 的 expected_output 只声明关心的字段，MatchesExpected 逐字段比对，
便于后续接入真实模型做回归时复用同一数据集结构。
"""

from collections.abc import Callable
from dataclasses import dataclass, field

from pydantic_ai.messages import ModelMessage, ModelRequest, ModelResponse, TextPart, ToolCallPart, ToolReturnPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.usage import UsageLimits
from pydantic_evals import Case, Dataset
from pydantic_evals.evaluators import Evaluator, EvaluatorContext

from agent_services.agent.runtime import ChatBlocked, RunBudget, run_chat
from agent_services.agent.todo_tools import TodoStore, build_todo_toolset
from agent_services.schemas.chat import ChatHistoryMessage
from agent_services.schemas.todo import TodoItem

Responder = Callable[[list[ModelMessage], AgentInfo], ModelResponse]


def _has_tool_return(messages: list[ModelMessage]) -> bool:
    return any(
        isinstance(message, ModelRequest) and any(isinstance(p, ToolReturnPart) for p in message.parts)
        for message in messages
    )


def _call_then_answer(tool_name: str, args: dict, answer: str) -> Responder:
    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        if not _has_tool_return(messages):
            return ModelResponse(parts=[ToolCallPart(tool_name=tool_name, args=args)])
        return ModelResponse(parts=[TextPart(answer)])

    return respond


def _always_call(tool_name: str) -> Responder:
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[ToolCallPart(tool_name=tool_name, args={})])

    return respond


def _answer_if_seen(marker: str, yes: str, no: str) -> Responder:
    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart(yes if marker in repr(messages) else no)])

    return respond


def _raise(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
    raise RuntimeError("provider down")


@dataclass
class Scenario:
    responder: Responder
    message: str = "go"
    todos: list[TodoItem] = field(default_factory=list)
    history: list[ChatHistoryMessage] = field(default_factory=list)
    skill_instructions: str | None = None
    budget: RunBudget = field(default_factory=RunBudget)


_HISTORY_WITH_NAME = [
    ChatHistoryMessage(role="USER", content="my name is Tanaka"),
    ChatHistoryMessage(role="ASSISTANT", content="Hello Tanaka!"),
    ChatHistoryMessage(role="USER", content="ok"),
    ChatHistoryMessage(role="ASSISTANT", content="noted"),
]

SCENARIOS: dict[str, Scenario] = {
    "skill-instructions-reach-model": Scenario(
        responder=_answer_if_seen("SKILL-MARKER-7", "skill-active", "skill-missing"),
        skill_instructions="SKILL-MARKER-7: follow the plan.",
    ),
    "todo-roundtrip-completes": Scenario(
        responder=_call_then_answer("complete_todo", {"todo_id": "t1"}, "done"),
        todos=[TodoItem(id="t1", title="Step 1", status="IN_PROGRESS")],
    ),
    "terminal-todo-reopen-fails-visibly": Scenario(
        responder=_call_then_answer(
            "update_todo_status", {"todo_id": "t1", "status": "IN_PROGRESS"}, "cannot reopen"
        ),
        todos=[TodoItem(id="t1", title="Step 1", status="COMPLETED")],
    ),
    "runaway-loop-hits-budget": Scenario(
        responder=_always_call("list_todos"),
        budget=RunBudget(usage_limits=UsageLimits(request_limit=3)),
    ),
    "history-within-budget-is-used": Scenario(
        responder=_answer_if_seen("Tanaka", "knows-name", "unknown-name"),
        history=_HISTORY_WITH_NAME,
        budget=RunBudget(history_max_chars=1000),
    ),
    "history-over-budget-is-trimmed": Scenario(
        responder=_answer_if_seen("Tanaka", "knows-name", "unknown-name"),
        history=_HISTORY_WITH_NAME,
        budget=RunBudget(history_max_chars=10),
    ),
    "provider-error-fails-honestly": Scenario(responder=_raise),
}


async def run_scenario(name: str) -> dict[str, object]:
    scenario = SCENARIOS[name]
    store = TodoStore(items=[item.model_copy() for item in scenario.todos])
    outcome = await run_chat(
        FunctionModel(scenario.responder),
        site_id="1",
        message=scenario.message,
        history=scenario.history or None,
        skill_instructions=scenario.skill_instructions,
        initial_todos=store.items or None,
        toolsets=[build_todo_toolset(store)],
        budget=scenario.budget,
    )
    todos = [[item.id, item.status] for item in store.items]
    if isinstance(outcome, ChatBlocked):
        return {"kind": "blocked", "error_code": outcome.error_code, "todos": todos}
    return {
        "kind": "reply",
        "reply": outcome.reply,
        "tools": [[tool["name"], tool["ok"]] for tool in outcome.tools],
        "todos": todos,
    }


@dataclass
class MatchesExpected(Evaluator[str, dict]):
    """expected_output 中声明的每个字段都必须与实际输出相等；未声明字段不比较。"""

    def evaluate(self, ctx: EvaluatorContext[str, dict]) -> dict[str, bool]:
        expected = ctx.expected_output or {}
        return {key: ctx.output.get(key) == value for key, value in expected.items()}


PLATFORM_DATASET = Dataset(
    name="agent-platform-behavior",
    cases=[
        Case(
            name="skill-instructions-reach-model",
            inputs="skill-instructions-reach-model",
            expected_output={"kind": "reply", "reply": "skill-active"},
        ),
        Case(
            name="todo-roundtrip-completes",
            inputs="todo-roundtrip-completes",
            expected_output={
                "kind": "reply",
                "tools": [["complete_todo", True]],
                "todos": [["t1", "COMPLETED"]],
            },
        ),
        Case(
            name="terminal-todo-reopen-fails-visibly",
            inputs="terminal-todo-reopen-fails-visibly",
            expected_output={
                "kind": "reply",
                "tools": [["update_todo_status", False]],
                "todos": [["t1", "COMPLETED"]],
            },
        ),
        Case(
            name="runaway-loop-hits-budget",
            inputs="runaway-loop-hits-budget",
            expected_output={"kind": "blocked", "error_code": "AGENT_BUDGET_EXCEEDED"},
        ),
        Case(
            name="history-within-budget-is-used",
            inputs="history-within-budget-is-used",
            expected_output={"kind": "reply", "reply": "knows-name"},
        ),
        Case(
            name="history-over-budget-is-trimmed",
            inputs="history-over-budget-is-trimmed",
            expected_output={"kind": "reply", "reply": "unknown-name"},
        ),
        Case(
            name="provider-error-fails-honestly",
            inputs="provider-error-fails-honestly",
            expected_output={"kind": "blocked", "error_code": "EXTERNAL_DEPENDENCY_BLOCKED"},
        ),
    ],
    evaluators=[MatchesExpected()],
)


async def test_platform_behavior_dataset() -> None:
    report = await PLATFORM_DATASET.evaluate(run_scenario)
    failures = [
        f"{case.name} / {name}"
        for case in report.cases
        for name, result in case.assertions.items()
        if not result.value
    ]
    assert not failures, f"platform eval failed: {failures}"
    assert not report.failures, f"platform eval task errors: {[f.name for f in report.failures]}"
    assert not any(case.evaluator_failures for case in report.cases), "evaluator raised"
    assert len(report.cases) == len(SCENARIOS)
