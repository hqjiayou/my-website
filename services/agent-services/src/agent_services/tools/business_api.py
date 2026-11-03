"""Java site-app internal API 客户端。

业务真相源在 Java；工具只读/写 Java internal 端点（X-Internal-Service-Token 鉴权），
禁止直连 Java 业务数据库。
"""

import logging
from typing import Any
from urllib.parse import quote

import httpx

from agent_services.api.errors import external_failure_message

logger = logging.getLogger(__name__)


class BusinessApiError(RuntimeError):
    """Java internal API 调用失败；工具层向模型返回可读错误，不伪造成功。"""


def _http_error_message(response: httpx.Response) -> str:
    """非 200 时只转发短业务 message。纯文本或无法解析的正文不进模型上下文。"""
    try:
        body = response.json()
    except ValueError:
        return f"Business API returned HTTP {response.status_code}."
    if isinstance(body, dict) and body.get("code") not in (None, 0):
        return _business_error_message(body)
    return f"Business API returned HTTP {response.status_code}."


def _business_error_message(body: object) -> str:
    """只转发 Java 的短文本 message。对象、URL、凭据形态不进模型上下文。"""
    message = body.get("message") if isinstance(body, dict) else None
    if not isinstance(message, str):
        return "Business API error: request failed"
    text = " ".join(message.split())
    if not text or "://" in text or "@" in text or len(text) > 300:
        return "Business API error: request failed"
    return f"Business API error: {text}"


class BusinessApiClient:
    def __init__(
        self,
        base_url: str,
        internal_service_token: str,
        *,
        timeout_seconds: float = 10.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._token = internal_service_token
        self._timeout = timeout_seconds
        self._transport = transport

    def _client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base_url,
            timeout=self._timeout,
            headers={"X-Internal-Service-Token": self._token},
            transport=self._transport,
        )

    async def _request(
        self,
        method: str,
        path: str,
        *,
        headers: dict[str, str] | None = None,
        **kwargs: Any,
    ) -> Any:
        async with self._client() as client:
            try:
                response = await client.request(method, path, headers=headers, **kwargs)
            except httpx.HTTPError as exc:
                logger.warning("Business API unreachable", exc_info=exc)
                raise BusinessApiError(external_failure_message("Business API unreachable", exc)) from exc
        if response.status_code != 200:
            raise BusinessApiError(_http_error_message(response))
        try:
            body = response.json()
        except ValueError as exc:
            raise BusinessApiError("Business API returned a non-JSON body.") from exc
        # Java ApiResponse 契约：code=0 成功。不把响应正文交给模型。
        if not isinstance(body, dict) or body.get("code") != 0:
            raise BusinessApiError(_business_error_message(body))
        return body.get("data")

    def _site_path(self, agent_id: str, suffix: str = "") -> str:
        return f"/internal/agent/v1/agents/{quote(agent_id, safe='')}{suffix}"

    async def get_site_summary(self, agent_id: str) -> Any:
        return await self._request("GET", self._site_path(agent_id))

    async def list_site_contents(
        self, agent_id: str, content_type: str | None = None, limit: int = 20
    ) -> Any:
        params: dict[str, Any] = {"limit": limit}
        if content_type:
            params["type"] = content_type
        return await self._request("GET", self._site_path(agent_id, "/contents"), params=params)

    async def list_knowledge_documents(
        self,
        agent_id: str,
        keyword: str | None = None,
        source_format: str | None = None,
        created_from: str | None = None,
        created_to: str | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Any:
        """枚举知识文档元数据（Issue #181）：Java 口径 limit 夹进 1..200、offset 下限 0。

        时间参数为 ISO-8601 字符串，原样透传给 Java 解析；解析失败按业务错误回给工具层。
        """
        params: dict[str, Any] = {"limit": limit, "offset": offset}
        if keyword:
            params["keyword"] = keyword
        if source_format:
            params["format"] = source_format
        if created_from:
            params["createdFrom"] = created_from
        if created_to:
            params["createdTo"] = created_to
        return await self._request(
            "GET", self._site_path(agent_id, "/knowledge-documents"), params=params
        )
