"""Java site-app internal API 客户端。

业务真相源在 Java；工具只读/写 Java internal 端点（X-Internal-Service-Token 鉴权），
禁止直连 Java 业务数据库。
"""

from typing import Any

import httpx


class BusinessApiError(RuntimeError):
    """Java internal API 调用失败；工具层向模型返回可读错误，不伪造成功。"""


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

    async def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        async with self._client() as client:
            try:
                response = await client.request(method, path, **kwargs)
            except httpx.HTTPError as exc:
                raise BusinessApiError(f"Business API unreachable: {exc}") from exc
        if response.status_code != 200:
            raise BusinessApiError(
                f"Business API returned HTTP {response.status_code}: {response.text[:200]}"
            )
        body = response.json()
        # Java ApiResponse 契约：code=0 成功
        if body.get("code") != 0:
            raise BusinessApiError(f"Business API error: {body.get('message')}")
        return body.get("data")

    async def get_site_summary(self, site_id: str) -> Any:
        return await self._request("GET", f"/internal/site/v1/sites/{site_id}")

    async def list_site_contents(
        self, site_id: str, content_type: str | None = None, limit: int = 20
    ) -> Any:
        params: dict[str, Any] = {"limit": limit}
        if content_type:
            params["type"] = content_type
        return await self._request(
            "GET", f"/internal/site/v1/sites/{site_id}/contents", params=params
        )

    async def create_reservation(
        self,
        site_id: str,
        *,
        customer_name: str,
        phone: str,
        service_type: str,
        reserved_at: str,
        note: str | None = None,
    ) -> Any:
        payload: dict[str, Any] = {
            "customerName": customer_name,
            "phone": phone,
            "serviceType": service_type,
            "reservedAt": reserved_at,
        }
        if note:
            payload["note"] = note
        return await self._request(
            "POST", f"/internal/site/v1/sites/{site_id}/reservations", json=payload
        )
