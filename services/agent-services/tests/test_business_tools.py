"""Phase 4 业务工具：Function Tools → Java internal API（httpx MockTransport，不出网）。"""


import httpx
from conftest import make_settings

from agent_services.agent.business_tools import build_business_toolset
from agent_services.tools.business_api import (
    BusinessApiClient,
    BusinessApiError,
)


def test_toolset_absent_without_base_url() -> None:
    assert build_business_toolset(make_settings(), "1") is None


def _api_response(data: object) -> httpx.Response:
    return httpx.Response(200, json={"code": 0, "message": "success", "data": data})


def _client_with(handler) -> BusinessApiClient:
    return BusinessApiClient(
        "http://java-internal", "test-internal-token", transport=httpx.MockTransport(handler)
    )


async def test_client_sends_token_and_site_scoped_path() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return _api_response({"id": 7, "siteName": "Cake Shop", "siteType": "cake"})

    data = await _client_with(handler).get_site_summary("7")
    assert data["siteName"] == "Cake Shop"
    request = captured[0]
    assert request.url.path == "/internal/agent/v1/agents/7"
    assert request.headers["X-Internal-Service-Token"] == "test-internal-token"


async def test_client_encodes_agent_id_and_hides_error_body() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        if request.url.path.endswith("/contents"):
            return httpx.Response(500, text="token=secret-stack")
        return httpx.Response(200, text="not-json")

    client = _client_with(handler)
    try:
        await client.list_site_contents("7/../other")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert "secret-stack" not in str(exc)
        assert "HTTP 500" in str(exc)
    assert b"/internal/agent/v1/agents/7%2F..%2Fother/contents" in captured[0].url.raw_path

    try:
        await client.get_site_summary("7")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert "not-json" not in str(exc)


async def test_client_hides_url_when_unreachable() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError(f"dial tcp {request.url}")

    try:
        await _client_with(handler).get_site_summary("7")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert str(exc) == "Business API unreachable: ConnectError"
        assert "java-internal" not in str(exc)


async def test_client_forwards_short_message_on_http_error() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"code": 1001, "message": "phone: must not be blank", "data": None})

    try:
        await _client_with(handler).get_site_summary("7")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert str(exc) == "Business API error: phone: must not be blank"


async def test_client_raises_on_business_error_body() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 1004, "message": "not found", "data": None})

    try:
        await _client_with(handler).get_site_summary("404")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert "not found" in str(exc)


async def test_client_hides_url_and_non_text_business_message() -> None:
    def url_handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={"code": 1, "message": "see https://internal.example/secret", "data": None},
        )

    try:
        await _client_with(url_handler).get_site_summary("1")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert str(exc) == "Business API error: request failed"
        assert "internal.example" not in str(exc)

    def object_handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 1, "message": {"url": "https://db.internal"}, "data": None})

    try:
        await _client_with(object_handler).get_site_summary("1")
        raise AssertionError("expected BusinessApiError")
    except BusinessApiError as exc:
        assert str(exc) == "Business API error: request failed"
        assert "db.internal" not in str(exc)


async def test_get_site_info_presents_site_type_as_neutral_category() -> None:
    """呈现给模型时 siteType 改名 category（值不变），避免被误读为行业定性。"""

    def handler(request: httpx.Request) -> httpx.Response:
        return _api_response(
            {"id": 7, "siteName": "Demo", "siteType": "PERSONAL", "description": "d"}
        )

    toolset = build_business_toolset(make_settings(), "7", client=_client_with(handler))
    assert toolset is not None
    tool = toolset.tools["get_site_info"]
    result = await tool.function()
    assert result == {
        "site": {"id": 7, "siteName": "Demo", "category": "PERSONAL", "description": "d"}
    }
