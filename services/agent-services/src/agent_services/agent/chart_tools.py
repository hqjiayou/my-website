"""图表卡工具 render_chart（Issue #179 UoW-3）：仅 preview 运行时注册。

模型给出 chart spec，运行时校验（visualization.build_chart_card）通过才发卡；
校验失败回结构化错误给模型、不产卡。工具本身不取数、不算数。
"""

from __future__ import annotations

import json
from typing import Any

from pydantic_ai.toolsets import FunctionToolset

from agent_services.agent.visualization import VisualizationCollector, build_chart_card

RENDER_CHART_TOOL_NAME = "render_chart"

CHART_DISPLAYED_TEXT = "Chart displayed to the user."


def build_chart_toolset(collector: VisualizationCollector) -> FunctionToolset:
    toolset = FunctionToolset()

    @toolset.tool_plain(name=RENDER_CHART_TOOL_NAME)
    async def render_chart(
        chart_type: str,
        series: list[dict[str, Any]],
        title: str | None = None,
    ) -> str:
        """Show a chart card to the user, rendered from the data you provide.

        Use this only when a chart genuinely serves the user: they ask to see
        a trend, a comparison, or a proportion, and you already have the exact
        numbers from tool results in this conversation. Do not invent numbers
        for a chart, and do not call this for one or two values that a sentence
        covers. After a successful call, keep your text brief: the chart is
        already visible, so do not repeat every value in prose.

        Args:
            chart_type: one of "line", "bar", "area", "pie". Use line/area for
                trends over time, bar for comparisons, pie for proportions
                (a single series).
            series: up to 4 series, each {"name": str, "points": [{"label": str,
                "value": number}, ...]} with at most 50 points per series.
                Every value must be a finite number taken from tool output.
            title: optional short chart title.
        """
        card, error = build_chart_card(
            {"type": chart_type, "title": title, "series": series}
        )
        if error is not None:
            return json.dumps(error, ensure_ascii=False)
        assert card is not None  # build_chart_card 二选一返回
        collector.record(card)
        return CHART_DISPLAYED_TEXT

    return toolset

