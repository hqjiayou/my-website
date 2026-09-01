"""Pydantic Evals 基线（Phase 2，测试体系第 4 层起点）。

用 FunctionModel 驱动 run_chat，不触达真实 Provider；
评估维度只锁定当前已实现的确定性行为（非空回复、历史被利用）。
Prompt/RAG 成型后在此扩展数据集与 evaluator。
"""

from dataclasses import dataclass

from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_evals import Case, Dataset
from pydantic_evals.evaluators import Evaluator, EvaluatorContext

from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.schemas.chat import ChatHistoryMessage


@dataclass
class NonEmptyReply(Evaluator[dict, str]):
    def evaluate(self, ctx: EvaluatorContext[dict, str]) -> bool:
        return bool(ctx.output and ctx.output.strip())


@dataclass
class ContainsExpected(Evaluator[dict, str]):
    def evaluate(self, ctx: EvaluatorContext[dict, str]) -> bool:
        expected = ctx.expected_output
        return expected is None or expected in ctx.output


def _echoing_model() -> FunctionModel:
    """确定性模型：回显历史中的名字，验证 multi-turn 上下文真实生效。"""

    def respond(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        flat = repr(messages)
        if "Tanaka" in flat:
            return ModelResponse(parts=[TextPart("Your name is Tanaka.")])
        return ModelResponse(parts=[TextPart("I can help with that.")])

    return FunctionModel(respond)


async def _run_case(inputs: dict) -> str:
    outcome = await run_chat(
        _echoing_model(),
        site_id=inputs["site_id"],
        message=inputs["message"],
        history=[ChatHistoryMessage(**h) for h in inputs.get("history", [])],
    )
    assert isinstance(outcome, ChatReply)
    return outcome.reply


async def test_evals_baseline_dataset() -> None:
    dataset = Dataset(
        name="agent-chat-baseline",
        cases=[
            Case(
                name="single-turn-reply",
                inputs={"site_id": "1", "message": "hello"},
                expected_output=None,
            ),
            Case(
                name="multi-turn-uses-history",
                inputs={
                    "site_id": "1",
                    "message": "what is my name?",
                    "history": [
                        {"role": "USER", "content": "my name is Tanaka"},
                        {"role": "ASSISTANT", "content": "Hello Tanaka!"},
                    ],
                },
                expected_output="Tanaka",
            ),
        ],
        evaluators=[NonEmptyReply(), ContainsExpected()],
    )

    report = await dataset.evaluate(_run_case)
    for case in report.cases:
        for name, result in case.assertions.items():
            assert result.value, f"eval failed: {case.name} / {name}"
