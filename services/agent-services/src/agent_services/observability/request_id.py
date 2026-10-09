"""X-Request-Id 关联（§22.7）：Java 网关生成 → HttpAgentClient 透传 → Python span / Agent run metadata。"""

import re
from contextvars import ContextVar

from fastapi import FastAPI
from opentelemetry import trace
from starlette.types import ASGIApp, Receive, Scope, Send

REQUEST_ID_HEADER = b"x-request-id"
# 头部来自内部调用方，但仍做白名单校验，避免把任意内容写进 span / 日志
_VALID_REQUEST_ID = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")

_request_id: ContextVar[str | None] = ContextVar("agent_request_id", default=None)


def current_request_id() -> str | None:
    return _request_id.get()


class _RequestIdMiddleware:
    """纯 ASGI 中间件：不包装响应体，SSE 流与断连取消语义保持原样（BaseHTTPMiddleware 会干扰）。"""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        raw = dict(scope.get("headers") or []).get(REQUEST_ID_HEADER, b"").decode("latin-1").strip()
        request_id = raw if _VALID_REQUEST_ID.match(raw) else None
        token = _request_id.set(request_id)
        try:
            if request_id:
                trace.get_current_span().set_attribute("request.id", request_id)
            await self.app(scope, receive, send)
        finally:
            _request_id.reset(token)


def install_request_id_middleware(app: FastAPI) -> None:
    app.add_middleware(_RequestIdMiddleware)
