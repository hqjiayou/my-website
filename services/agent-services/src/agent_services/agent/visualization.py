"""回复内卡片契约（Issue #179）：信封、chart spec 校验与本轮收集器。

卡片只活在流式呈现里：工具在 run 内把卡片信封写进 VisualizationCollector，
stream_runtime 在工具结果事件后按序 drain 成 VISUALIZATION 事件发给 Java。
卡片不落库、不进 DONE，也不进同步 chat 响应。
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Any, Literal

from pydantic import BaseModel, Field, ValidationError, field_validator

CARD_KIND_WEATHER = "weather"
CARD_KIND_CHART = "chart"

# chart spec 上限（契约里定死，校验与测试同口径）：系列数、每系列点数取保守值，
# 防止模型一次塞爆上下文与前端渲染。
MAX_CHART_SERIES = 4
MAX_CHART_POINTS_PER_SERIES = 50
MAX_CHART_TITLE_CHARS = 120
MAX_CHART_NAME_CHARS = 64
MAX_CHART_LABEL_CHARS = 64

ChartType = Literal["line", "bar", "area", "pie"]


def card_envelope(kind: str, payload: dict[str, Any], title: str | None = None) -> dict[str, Any]:
    """卡片信封：kind + payload + 可选 title（空 title 不写出）。"""
    envelope: dict[str, Any] = {"kind": kind, "payload": payload}
    if title is not None and title.strip():
        envelope["title"] = title.strip()
    return envelope


@dataclass
class VisualizationCollector:
    """一次 run 内的待发卡片队列（样板同 KnowledgeSearchCollector）。

    工具 record，stream_runtime 在每个 TOOL_RESULT 后 drain 发 VISUALIZATION，
    run 收尾再 drain 一次兜底（grounding 复跑等晚到的卡片不丢）。
    """

    cards: list[dict[str, Any]] = field(default_factory=list)

    def record(self, card: dict[str, Any]) -> None:
        self.cards.append(card)

    def drain(self) -> list[dict[str, Any]]:
        drained, self.cards = self.cards, []
        return drained


class ChartPoint(BaseModel):
    label: str = Field(min_length=1, max_length=MAX_CHART_LABEL_CHARS)
    value: float = Field(allow_inf_nan=False)

    @field_validator("value", mode="before")
    @classmethod
    def reject_bool_value(cls, value: object) -> object:
        # bool 是 int 子类、pydantic 会把 True 宽容成 1.0；图表数值不收布尔。
        if isinstance(value, bool):
            raise ValueError("chart value must be a number, not a boolean")
        return value


class ChartSeries(BaseModel):
    name: str = Field(min_length=1, max_length=MAX_CHART_NAME_CHARS)
    points: list[ChartPoint] = Field(min_length=1, max_length=MAX_CHART_POINTS_PER_SERIES)


class ChartSpec(BaseModel):
    """模型给出的 chart spec 原形（含 title）；过校验才转成卡片信封。"""

    type: ChartType
    title: str | None = Field(default=None, max_length=MAX_CHART_TITLE_CHARS)
    series: list[ChartSeries] = Field(min_length=1, max_length=MAX_CHART_SERIES)


def build_chart_card(raw: object) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    """校验 chart spec：成功回 (卡片信封, None)，失败回 (None, 结构化错误)。

    错误回给模型看（工具结果），不产卡：errorCode + issues（字段路径与原因）。
    """
    try:
        spec = ChartSpec.model_validate(raw)
    except ValidationError as exc:
        issues = [
            {"field": ".".join(str(part) for part in error["loc"]), "message": error["msg"]}
            for error in exc.errors()[:8]
        ]
        return None, {
            "errorCode": "INVALID_CHART_SPEC",
            "message": (
                "chart spec failed validation; no chart was shown. "
                f"Limits: at most {MAX_CHART_SERIES} series, at most "
                f"{MAX_CHART_POINTS_PER_SERIES} points per series, finite numeric values only."
            ),
            "issues": issues,
        }
    payload: dict[str, Any] = {
        "type": spec.type,
        "series": [
            {
                "name": series.name,
                "points": [
                    {"label": point.label, "value": _wire_number(point.value)}
                    for point in series.points
                ],
            }
            for series in spec.series
        ],
    }
    return card_envelope(CARD_KIND_CHART, payload, spec.title), None


def _wire_number(value: float) -> int | float:
    """整数值按 int 写出（12 而非 12.0），与 citations 的数值口径一致；有限性已由模型校验。"""
    if math.isfinite(value) and float(value).is_integer():
        return int(value)
    return value
