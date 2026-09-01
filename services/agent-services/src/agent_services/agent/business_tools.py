"""业务 Function Tools（Phase 4）：模型可调用的站点业务操作。

- site_id 由请求上下文闭包注入（trusted），永不接受模型生成的 site_id；
- 所有工具回调 Java internal API（业务真相源），失败向模型返回可读错误，不伪造成功。
"""

from pydantic_ai.toolsets import FunctionToolset

from agent_services.config import Settings
from agent_services.tools.business_api import BusinessApiClient, BusinessApiError


def build_business_toolset(
    settings: Settings,
    site_id: str,
    *,
    client: BusinessApiClient | None = None,
) -> FunctionToolset | None:
    """BUSINESS_API_BASE_URL 未配置时返回 None（纯问答模式，不注册工具）。"""
    if client is None:
        base_url = settings.business_api_base_url.strip()
        if not base_url:
            return None
        client = BusinessApiClient(base_url, settings.agent_internal_service_token)

    toolset = FunctionToolset()

    @toolset.tool_plain
    async def get_site_info() -> dict:
        """Get basic information about the current site (name, type, description)."""
        try:
            return {"site": await client.get_site_summary(site_id)}
        except BusinessApiError as exc:
            return {"error": str(exc)}

    @toolset.tool_plain
    async def list_site_contents(content_type: str | None = None) -> dict:
        """List the site's published contents (services, FAQ, news, cases, about...).

        Args:
            content_type: optional filter, one of hero/service/case/faq/news/about/cta.
        """
        try:
            return {"contents": await client.list_site_contents(site_id, content_type)}
        except BusinessApiError as exc:
            return {"error": str(exc)}

    @toolset.tool_plain
    async def create_reservation(
        customer_name: str,
        phone: str,
        service_type: str,
        reserved_at: str,
        note: str | None = None,
    ) -> dict:
        """Create a reservation for the customer on this site.

        Only call this after the customer explicitly confirmed name, phone,
        service type and the reservation time.

        Args:
            customer_name: customer's name.
            phone: customer's contact phone number.
            service_type: requested service described in a short phrase.
            reserved_at: reservation time, ISO-8601 with offset (e.g. 2026-09-01T15:00:00+09:00).
            note: optional extra request from the customer.
        """
        try:
            reservation = await client.create_reservation(
                site_id,
                customer_name=customer_name,
                phone=phone,
                service_type=service_type,
                reserved_at=reserved_at,
                note=note,
            )
            return {"reservation": reservation}
        except BusinessApiError as exc:
            return {"error": str(exc)}

    return toolset
