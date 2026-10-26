"""业务 Function Tools（Phase 4）：模型可调用的站点业务操作。

- site_id 由请求上下文闭包注入（trusted），永不接受模型生成的 site_id；
- 所有工具回调 Java internal API（业务真相源），失败向模型返回可读错误，不伪造成功。
"""

from pydantic_ai.toolsets import FunctionToolset

from agent_services.config import Settings
from agent_services.tools.business_api import BusinessApiClient, BusinessApiError


def _present_site_summary(summary: object) -> object:
    """呈现给模型时把 siteType 改名为中性的 category（用途标签原文不变）。

    siteType 现为自由用途标签而非行业分类；改名避免模型把标签误读为行业定性
    （曾出现把演示站说成诊所的臆造）。Java 契约字段本身不动。
    """
    if isinstance(summary, dict) and "siteType" in summary:
        presented = dict(summary)
        presented["category"] = presented.pop("siteType")
        return presented
    return summary


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
        """Get basic information about the current site (name, category tag, description)."""
        try:
            return {"site": _present_site_summary(await client.get_site_summary(site_id))}
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

    return toolset
