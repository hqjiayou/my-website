"""Phase 4 业务工具：Function Tools → Java internal API（httpx MockTransport，不出网）。"""

import json

import httpx
from conftest import make_settings
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart, ToolCallPart, ToolReturnPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.agent.business_tools import build_business_toolset
from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.tools.business_api import (
    BusinessApiClient,
    BusinessApiError,
    reservation_idempotency_key,
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
    assert request.url.path == "/internal/site/v1/sites/7"
    assert request.headers["X-Internal-Service-Token"] == "test-internal-token"


async def test_client_encodes_site_id_and_hides_error_body() -> None:
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
    assert b"/internal/site/v1/sites/7%2F..%2Fother/contents" in captured[0].url.raw_path

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
        await _client_with(handler).create_reservation(
            "7",
            customer_name="Tanaka",
            phone="",
            service_type="cut",
            reserved_at="2026-09-02T10:00:00+09:00",
        )
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


async def test_create_reservation_tool_calls_java_internal_api() -> None:
    """FunctionModel 先发 tool call，再产出最终回复；断言 Java internal API 真实被调、site_id 由闭包注入。"""
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return _api_response(
            {"id": 55, "customerName": "Tanaka", "serviceType": "cut", "status": "NEW"}
        )

    client = _client_with(handler)
    toolset = build_business_toolset(make_settings(), "42", client=client)
    assert toolset is not None

    def model_fn(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        has_tool_result = any(
            isinstance(part, ToolReturnPart) and part.tool_name == "create_reservation"
            for message in messages
            for part in message.parts
        )
        if not has_tool_result:
            return ModelResponse(parts=[
                ToolCallPart(
                    tool_name="create_reservation",
                    args={
                        "customer_name": "Tanaka",
                        "phone": "090-0000-0000",
                        "service_type": "cut",
                        "reserved_at": "2026-09-02T10:00:00+09:00",
                    },
                )
            ])
        return ModelResponse(parts=[TextPart("Reservation #55 confirmed.")])

    outcome = await run_chat(
        FunctionModel(model_fn), site_id="42", message="book me a cut tomorrow 10am", toolsets=[toolset]
    )
    assert isinstance(outcome, ChatReply)
    assert "55" in outcome.reply

    request = captured[0]
    # site_id 来自请求上下文闭包（trusted），模型参数里没有 site_id
    assert request.url.path == "/internal/site/v1/sites/42/reservations"
    assert request.headers["Idempotency-Key"] == reservation_idempotency_key(
        "42",
        {
            "customerName": "Tanaka",
            "phone": "090-0000-0000",
            "serviceType": "cut",
            "reservedAt": "2026-09-02T10:00:00+09:00",
        },
    )
    payload = json.loads(request.content)
    assert payload["customerName"] == "Tanaka"
    assert payload["reservedAt"] == "2026-09-02T10:00:00+09:00"


def test_reservation_idempotency_key_ignores_dict_order() -> None:
    left = {"phone": "1", "customerName": "A", "reservedAt": "t", "serviceType": "cut"}
    right = {"serviceType": "cut", "reservedAt": "t", "customerName": "A", "phone": "1"}
    assert reservation_idempotency_key("9", left) == reservation_idempotency_key("9", right)
    assert reservation_idempotency_key("9", left).startswith("rsv-")
    assert len(reservation_idempotency_key("9", left)) == 4 + 64
    changed = dict(left)
    changed["phone"] = "2"
    assert reservation_idempotency_key("9", left) != reservation_idempotency_key("9", changed)
    assert reservation_idempotency_key("9", left) != reservation_idempotency_key("8", left)


async def test_create_reservation_sends_stable_idempotency_key() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return _api_response({"id": 1})

    client = _client_with(handler)
    kwargs = {
        "customer_name": "Tanaka",
        "phone": "090",
        "service_type": "cut",
        "reserved_at": "2026-09-02T10:00:00+09:00",
    }
    await client.create_reservation("7", **kwargs)
    await client.create_reservation("7", **kwargs)
    await client.create_reservation("7", **kwargs, note="window")
    await client.create_reservation("8", **kwargs)

    keys = [request.headers["Idempotency-Key"] for request in captured]
    payload = {
        "customerName": "Tanaka",
        "phone": "090",
        "serviceType": "cut",
        "reservedAt": "2026-09-02T10:00:00+09:00",
    }
    assert keys[0] == keys[1] == reservation_idempotency_key("7", payload)
    assert keys[2] == reservation_idempotency_key("7", {**payload, "note": "window"})
    assert keys[2] != keys[0]
    assert keys[3] == reservation_idempotency_key("8", payload)
    assert keys[3] != keys[0]
    assert captured[0].headers["X-Internal-Service-Token"] == "test-internal-token"
