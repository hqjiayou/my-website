# syntax=docker/dockerfile:1

# agent-services 生产镜像（internal only，不经 gateway 暴露）
FROM ghcr.io/astral-sh/uv:python3.13-bookworm-slim AS builder
WORKDIR /app
COPY pyproject.toml uv.lock .python-version README.md ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
COPY skills ./skills
ENV UV_PREVIEW_FEATURES=extra-build-dependencies
RUN uv pip install hatchling && uv sync --frozen --no-dev --no-editable

FROM python:3.13-slim-bookworm AS runtime
WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=3001

# 非 root 运行
RUN useradd --create-home --shell /bin/bash appuser
USER appuser

COPY --from=builder --chown=appuser:appuser /app/.venv /app/.venv
COPY --from=builder --chown=appuser:appuser /app/src /app/src
COPY --from=builder --chown=appuser:appuser /app/skills /app/skills

ENV AGENT_SKILLS_DIR=/app/skills

ENV PATH="/app/.venv/bin:$PATH"

EXPOSE 3001

# 公开探活（liveness）；readiness 用 /internal/v1/health + token（由编排层配置）
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:3001/health')" || exit 1

CMD ["uvicorn", "agent_services.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "3001"]
