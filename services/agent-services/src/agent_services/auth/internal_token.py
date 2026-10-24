import secrets

from fastapi import Request

from agent_services.api.errors import InternalApiError

INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token"


def assert_token_configured(token: str) -> None:
    """启动期校验：token 缺失是安全配置错误，必须 fail-fast，而非运行期 503。"""
    if not token.strip():
        raise RuntimeError(
            "AGENT_INTERNAL_SERVICE_TOKEN is not configured. "
            "Refusing to start: internal endpoints would be unprotected."
        )


def require_internal_service_token(request: Request) -> None:
    expected: str = request.app.state.settings.agent_internal_service_token.strip()
    provided = request.headers.get(INTERNAL_SERVICE_TOKEN_HEADER, "")
    # compare_digest 只接受 ASCII str，非 ASCII 会 TypeError 变成未捕获 500。
    # 先编成 UTF-8 bytes，校验失败仍是 401；token 永不写日志。
    if not provided or not secrets.compare_digest(provided.encode("utf-8"), expected.encode("utf-8")):
        raise InternalApiError(
            status_code=401,
            error_code="UNAUTHORIZED",
            message="Invalid or missing internal service token.",
        )
