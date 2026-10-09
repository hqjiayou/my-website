"""Phase 3 检索：分块、site_id 强制过滤、internal 同步端点、chat 检索增强。"""

import json

import httpx
from fastapi.testclient import TestClient
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.app import create_app
from agent_services.retrieval.chunking import split_text
from agent_services.retrieval.embeddings import OpenAIEmbeddingClient
from agent_services.retrieval.service import RetrievalService
from agent_services.retrieval.store import RetrievedChunk
from conftest import TOKEN_HEADER, make_settings

# ---------------------------------------------------------------- chunking


def test_split_text_short_content_single_chunk() -> None:
    assert split_text("hello world") == ["hello world"]


def test_split_text_empty_returns_no_chunks() -> None:
    assert split_text("   \n\n  ") == []


def test_split_text_merges_paragraphs_within_limit() -> None:
    chunks = split_text("para one\n\npara two", max_chars=200)
    assert chunks == ["para one\n\npara two"]


async def test_embedding_client_posts_openai_compatible_endpoint() -> None:
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("authorization")
        captured["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={"data": [{"index": 1, "embedding": [0.2]}, {"index": 0, "embedding": [0.1]}]},
        )

    client = OpenAIEmbeddingClient(
        "text-embedding-3-small",
        api_key="sk-test",
        base_url="http://embed.local/v1",
        client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )
    assert await client.embed(["a", "b"]) == [[0.1], [0.2]]
    assert captured["url"] == "http://embed.local/v1/embeddings"
    assert captured["auth"] == "Bearer sk-test"
    assert captured["body"] == {"model": "text-embedding-3-small", "input": ["a", "b"]}


def test_split_text_splits_long_paragraph_with_overlap() -> None:
    content = "x" * 2500
    chunks = split_text(content, max_chars=1000, overlap=100)
    assert all(len(c) <= 1000 for c in chunks)
    assert sum(len(c) for c in chunks) >= 2500  # overlap 不丢内容


# ---------------------------------------------------------------- fakes


class FakeEmbedder:
    def __init__(self) -> None:
        self.seen: list[list[str]] = []

    async def embed(self, texts: list[str]) -> list[list[float]]:
        self.seen.append(texts)
        return [[0.1, 0.2, 0.3] for _ in texts]


class FakeStore:
    """in-memory store；验证 service 层把 site_id 全链路传入。"""

    def __init__(self) -> None:
        self.docs: dict[tuple[str, str], list[str]] = {}
        self.search_calls: list[str] = []

    async def upsert_document(self, *, site_id, source_id, title, chunks, embeddings) -> int:
        assert len(chunks) == len(embeddings)
        self.docs[(site_id, source_id)] = list(chunks)
        return len(chunks)

    async def delete_document(self, *, site_id, source_id) -> None:
        self.docs.pop((site_id, source_id), None)

    async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
        self.search_calls.append(site_id)
        results = []
        for (sid, source_id), chunks in self.docs.items():
            if sid != site_id:  # site_id 强制过滤
                continue
            for i, content in enumerate(chunks[:top_k]):
                results.append(
                    RetrievedChunk(source_id=source_id, chunk_index=i, content=content, score=0.9)
                )
        return results[:top_k]

    async def close(self) -> None:
        pass


def _service(store: FakeStore | None = None) -> tuple[RetrievalService, FakeStore]:
    fake_store = store or FakeStore()
    return RetrievalService(FakeEmbedder(), fake_store), fake_store


# ---------------------------------------------------------------- service


async def test_service_upsert_and_site_scoped_search() -> None:
    service, store = _service()
    await service.upsert_document(site_id="1", source_id="kb:1", title="t", content="alpha facts")
    await service.upsert_document(site_id="2", source_id="kb:9", title="t", content="beta facts")

    hits_site1 = await service.search(site_id="1", query="facts")
    assert [h.content for h in hits_site1] == ["alpha facts"]
    # 站点隔离：site 2 的内容绝不可出现在 site 1 的检索结果
    assert all("beta" not in h.content for h in hits_site1)


async def test_service_retrieve_context_degrades_on_store_failure() -> None:
    class BrokenStore(FakeStore):
        async def search(self, **kwargs):
            raise RuntimeError("db down")

    service, _ = _service(BrokenStore())
    assert await service.retrieve_context(site_id="1", query="q") == []


# ---------------------------------------------------------------- internal API


def test_retrieval_upsert_blocked_without_configuration() -> None:
    client = TestClient(create_app(make_settings()))
    response = client.put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "sourceId": "kb:1", "title": "t", "content": "hello"},
    )
    assert response.status_code == 503
    body = response.json()
    assert body["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"
    assert body["providerStatus"] == "BLOCKED"


def test_retrieval_upsert_and_delete_with_service() -> None:
    service, store = _service()
    client = TestClient(create_app(make_settings(), retrieval_service=service))

    response = client.put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "sourceId": "kb:5", "title": "FAQ", "content": "opening hours 9-18"},
    )
    assert response.status_code == 200
    assert response.json() == {"ok": True, "data": {"chunks": 1}}
    assert ("1", "kb:5") in store.docs

    response = client.request(
        "DELETE",
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        params={"siteId": "1", "sourceId": "kb:5"},
    )
    assert response.status_code == 200
    assert ("1", "kb:5") not in store.docs


# ---------------------------------------------------------------- chat 检索增强


async def test_run_chat_injects_retrieval_context_into_system_prompt() -> None:
    seen: list[str] = []

    def capture(messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        seen.append(repr(messages))
        return ModelResponse(parts=[TextPart("ok")])

    outcome = await run_chat(
        FunctionModel(capture),
        site_id="1",
        message="when are you open?",
        retrieval_context=["opening hours 9-18"],
    )
    assert isinstance(outcome, ChatReply)
    assert any("opening hours 9-18" in r for r in seen)


def test_chat_endpoint_uses_retrieval_service() -> None:
    service, _ = _service()
    handle = ModelHandle(
        model=TestModel(custom_output_text="answered"), spec="test:model", blocked_reason=None
    )
    client = TestClient(create_app(make_settings(), model_handle=handle, retrieval_service=service))
    client.put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"siteId": "7", "sourceId": "kb:1", "title": "t", "content": "site seven knowledge"},
    )

    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "7", "message": "hello"},
    )
    assert response.status_code == 200
    assert response.json()["data"]["providerStatus"] == "LIVE"
