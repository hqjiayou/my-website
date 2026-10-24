"""OpenTelemetry 初始化（部署环境通过环境变量启用，本地默认 no-op）。"""

import logging
import os

from fastapi import FastAPI

logger = logging.getLogger(__name__)


def setup_otel(app: FastAPI, service_name: str = "agent-services") -> None:
    endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
    if not endpoint:
        return

    try:
        from opentelemetry import trace
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import (
            OTLPSpanExporter,
        )
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
    except ImportError:
        logger.warning("OTEL_EXPORTER_OTLP_ENDPOINT is set but OpenTelemetry packages are missing")
        return

    resource = Resource.create(
        {
            "service.name": os.getenv("OTEL_SERVICE_NAME", service_name),
            "deployment.environment": os.getenv("OTEL_DEPLOYMENT_ENVIRONMENT", "unknown"),
        }
    )
    provider = TracerProvider(resource=resource)
    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint)))
    trace.set_tracer_provider(provider)
    FastAPIInstrumentor.instrument_app(app)
    _instrument_agents(provider)
    logger.info("OpenTelemetry enabled service=%s", service_name)


def _instrument_agents(provider: object) -> None:
    """Agent run / 模型请求 / 工具调用 span（GenAI 语义约定，§22.7）。

    默认不记录 prompt / completion / 工具参数正文：可能含终端用户 PII 与知识库内容；
    仅在排障环境显式设置 AGENT_OTEL_INCLUDE_CONTENT=true。
    """
    from pydantic_ai import Agent
    from pydantic_ai.models.instrumented import InstrumentationSettings

    include_content = os.getenv("AGENT_OTEL_INCLUDE_CONTENT", "").strip().lower() == "true"
    Agent.instrument_all(
        InstrumentationSettings(
            tracer_provider=provider,  # type: ignore[arg-type]
            include_content=include_content,
            include_binary_content=include_content,
        )
    )
