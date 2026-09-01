"""内部 API 错误 → 顶层扁平 JSON。

Java HttpAgentClient.mapErrorBody 读取顶层 errorCode / providerStatus；
禁止使用 FastAPI HTTPException(detail=...)（会包一层 {"detail": {...}} 破坏契约）。
"""

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse


class InternalApiError(Exception):
    def __init__(
        self,
        status_code: int,
        error_code: str,
        message: str,
        provider_status: str | None = None,
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


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(InternalApiError)
    async def internal_api_error_handler(_request: Request, exc: InternalApiError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content=exc.body)
