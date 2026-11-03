"""Issue #181 知识文档枚举：list_knowledge_documents 工具 → Java internal API（httpx MockTransport，不出网）。

覆盖：正常清单（含 total/truncated 透传）、空结果、过滤参数透传、上限截断标记、
上游错误降级回 {"error": ...}、未配 Java 通道时如实 unavailable、工厂注册（正式/试聊不门控）。
"""

import httpx
from conftest import make_settings

from agent_services.agent.knowledge_tools import build_knowledge_toolset
from agent_services.agent.todo_tools import TodoStore
from agent_services.agent.toolset_factory import build_execution_toolsets
from agent_services.retrieval.store import RetrievedChunk
from agent_services.tools.business_api import BusinessApiClient


class _FakeRetrieval:
    degraded = False

    async def retrieve_chunks(self, *, agent_id: str, query: str, **kwargs: object) -> list[RetrievedChunk]:
        return [RetrievedChunk(source_id="kb:1", chunk_index=0, content="KB-FACT", score=0.9)]


def _api_response(data: object) -> httpx.Response:
    return httpx.Response(200, json={"code": 0, "message": "success", "data": data})


def _client_with(handler) -> BusinessApiClient:
    return BusinessApiClient(
        "http://java-internal", "test-internal-token", transport=httpx.MockTransport(handler)
    )


def _list_payload(items: list[dict], total: int, *, limit: int = 100, offset: int = 0) -> dict:
    return {
        "items": items,
        "total": total,
        "limit": limit,
        "offset": offset,
        "truncated": offset + len(items) < total,
    }


_ITEM_JAN = {
    "id": 14,
    "sourceId": "kb:14",
    "title": "2026年1月工资单",
    "sourceFormat": "xlsx",
    "originalFilename": "payslip-2026-01.xlsx",
    "indexStatus": "INDEXED",
    "createdAt": "2026-02-01T10:00:00+09:00",
    "updatedAt": "2026-02-01T10:00:00+09:00",
}
_ITEM_FEB = {**_ITEM_JAN, "id": 15, "sourceId": "kb:15", "title": "2026年2月工资单"}


async def _call_list(toolset: object, **kwargs: object) -> dict:
    tool = toolset.tools["list_knowledge_documents"]  # type: ignore[attr-defined]
    return await tool.function(**kwargs)


def _toolset_with(handler) -> object:
    return build_knowledge_toolset(
        "42", _FakeRetrieval(), client=_client_with(handler)  # type: ignore[arg-type]
    )


async def test_client_sends_token_path_and_filter_params() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return _api_response(_list_payload([_ITEM_JAN], 1))

    data = await _client_with(handler).list_knowledge_documents(
        "42",
        keyword="工资单",
        source_format="xlsx",
        created_from="2026-01-01T00:00:00+09:00",
        created_to="2026-03-31T23:59:59+09:00",
        limit=50,
        offset=10,
    )
    assert data["total"] == 1
    request = captured[0]
    assert request.url.path == "/internal/agent/v1/agents/42/knowledge-documents"
    assert request.headers["X-Internal-Service-Token"] == "test-internal-token"
    params = request.url.params
    assert params["keyword"] == "工资单"
    assert params["format"] == "xlsx"
    assert params["createdFrom"] == "2026-01-01T00:00:00+09:00"
    assert params["createdTo"] == "2026-03-31T23:59:59+09:00"
    assert params["limit"] == "50"
    assert params["offset"] == "10"


async def test_tool_returns_documents_with_total_and_truncated() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return _api_response(_list_payload([_ITEM_JAN, _ITEM_FEB], 3, limit=2))

    result = await _call_list(_toolset_with(handler), limit=2)
    assert result["documents"] == [_ITEM_JAN, _ITEM_FEB]
    assert result["total"] == 3
    assert result["limit"] == 2
    assert result["offset"] == 0
    # total=3、本页 2 条 → 截断为真，模型须翻页取全。
    assert result["truncated"] is True


async def test_tool_returns_empty_list_for_no_documents() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return _api_response(_list_payload([], 0))

    result = await _call_list(_toolset_with(handler), keyword="不存在的文档类")
    assert result == {"documents": [], "total": 0, "limit": 100, "offset": 0, "truncated": False}


async def test_tool_forwards_filters_to_java() -> None:
    captured: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        captured.append(request)
        return _api_response(_list_payload([], 0))

    await _call_list(
        _toolset_with(handler),
        keyword="发票",
        source_format="pdf",
        created_from="2026-01-01T00:00:00+09:00",
        offset=200,
    )
    params = captured[0].url.params
    assert params["keyword"] == "发票"
    assert params["format"] == "pdf"
    assert params["createdFrom"] == "2026-01-01T00:00:00+09:00"
    assert "createdTo" not in params
    assert params["offset"] == "200"


async def test_tool_returns_error_dict_on_upstream_failure() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, text="token=<redacted>")

    result = await _call_list(_toolset_with(handler))
    assert "error" in result
    assert "HTTP 500" in result["error"]
    assert "secret-stack" not in result["error"]


async def test_tool_returns_error_dict_on_unexpected_payload() -> None:
    def handler(_request: httpx.Request) -> httpx.Response:
        return _api_response(["not-a-list-dto"])

    result = await _call_list(_toolset_with(handler))
    assert result == {"error": "Business API returned an unexpected document list."}


def test_list_tool_not_registered_without_java_client() -> None:
    # 无 Java 通道时不注册（与 business tools 未配置不注册同口径）：
    # 研究子 agent 等形态保持只有知识搜索，不给注定失败的工具。
    toolset = build_knowledge_toolset("42", _FakeRetrieval())  # type: ignore[arg-type]
    assert "search_site_knowledge" in toolset.tools
    assert "list_knowledge_documents" not in toolset.tools
    toolsets = build_execution_toolsets(
        make_settings(),
        "42",
        todo_store=TodoStore(),
        retrieval=_FakeRetrieval(),  # type: ignore[arg-type]
    )
    assert "list_knowledge_documents" not in _tool_names(toolsets)


def _tool_names(toolsets: list[object]) -> set[str]:
    names: set[str] = set()
    for toolset in toolsets:
        tools = getattr(toolset, "tools", None)
        if isinstance(tools, dict):
            names.update(tools.keys())
    return names


def test_factory_registers_list_tool_for_formal_and_preview() -> None:
    settings = make_settings(business_api_base_url="http://java-internal")
    for preview in (False, True):
        toolsets = build_execution_toolsets(
            settings,
            "42",
            todo_store=TodoStore(),
            retrieval=_FakeRetrieval(),  # type: ignore[arg-type]
            preview=preview,
        )
        names = _tool_names(toolsets)
        assert "list_knowledge_documents" in names
        assert "search_site_knowledge" in names


def test_factory_registers_list_tool_with_injected_client() -> None:
    toolsets = build_execution_toolsets(
        make_settings(),
        "42",
        todo_store=TodoStore(),
        client=_client_with(lambda _request: _api_response(_list_payload([], 0))),
        retrieval=_FakeRetrieval(),  # type: ignore[arg-type]
    )
    assert "list_knowledge_documents" in _tool_names(toolsets)
