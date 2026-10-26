"""内部 API 错误 → 顶层扁平 JSON。

Java HttpAgentClient.mapErrorBody 读取顶层 errorCode / providerStatus；
禁止使用 FastAPI HTTPException(detail=...)（会包一层 {"detail": {...}} 破坏契约）。
"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

logger = logging.getLogger(__name__)


class InternalApiError(Exception):
    def __init__(
        self,
        status_code: int,
        error_code: str,
        message: str,
        provider_status: str | None = None,
        details: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.body: dict[str, object] = {
            "ok": False,
            "errorCode": error_code,
            "message": message,
        }
        if provider_status is not None:
            self.body["providerStatus"] = provider_status
        if details:
            self.body.update(details)


def external_failure_message(prefix: str, exc: BaseException) -> str:
    """回给 Java 或模型的失败说明只含异常类型。str(exc) 常含 URL、DSN 或上游正文。"""
    return f"{prefix}: {type(exc).__name__}"


def _describe_validation_error(exc: RequestValidationError) -> str:
    # 只回字段路径与规则，不回显输入值（可能含用户消息/大 payload）
    parts: list[str] = []
    for error in exc.errors()[:5]:
        loc = ".".join(str(item) for item in error.get("loc", ()) if item != "body")
        parts.append(f"{loc or 'body'}: {error.get('msg', 'invalid')}")
    return "Invalid request: " + "; ".join(parts) if parts else "Invalid request."


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(InternalApiError)
    async def internal_api_error_handler(_request: Request, exc: InternalApiError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=exc.body)

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(_request: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=400,
            content={"ok": False, "errorCode": "BAD_REQUEST", "message": _describe_validation_error(exc)},
        )

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(_request: Request, exc: Exception) -> JSONResponse:
        # 查找按异常 MRO 走，更具体的 InternalApiError / RequestValidationError 仍优先。
        # 响应只给固定文案；完整堆栈留在服务端日志。
        logger.error("Unhandled internal error", exc_info=(type(exc), exc, exc.__traceback__))
        return JSONResponse(
            status_code=500,
            content={"ok": False, "errorCode": "INTERNAL_ERROR", "message": "Internal error."},
        )
