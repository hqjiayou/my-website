"""Phase 3 检索：分块、site_id 强制过滤、internal 同步端点、chat 检索增强。"""

import json

import httpx
import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel
from pydantic_ai.models.test import TestModel

from agent_services.agent.model_factory import ModelHandle
from agent_services.agent.runtime import ChatReply, run_chat
from agent_services.app import create_app
from agent_services.retrieval.chunking import split_text
from agent_services.retrieval.embeddings import (
    GoogleEmbeddingClient,
    OpenAIEmbeddingClient,
    create_embedding_client,
)
from agent_services.retrieval.service import (
    DEFAULT_TOP_K,
    MAX_UPSERT_CHUNKS,
    RetrievalService,
    create_retrieval_service,
)
from agent_services.retrieval.store import PgVectorRetrievalStore, RetrievedChunk
from agent_services.schemas.retrieval import MAX_CONTENT_LENGTH

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


async def test_embedding_client_batches_long_inputs() -> None:
    calls: list[list[str]] = []

    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        calls.append(list(body["input"]))
        data = [{"index": index, "embedding": [float(index)]} for index, _ in enumerate(body["input"])]
        return httpx.Response(200, json={"data": data})

    client = OpenAIEmbeddingClient(
        "text-embedding-3-small",
        api_key="sk-test",
        base_url="http://embed.local/v1",
        client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
        batch_size=2,
    )
    assert await client.embed(["a", "b", "c"]) == [[0.0], [1.0], [0.0]]
    assert calls == [["a", "b"], ["c"]]


async def test_embedding_client_rejects_count_mismatch() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"data": [{"index": 0, "embedding": [0.1]}]})

    client = OpenAIEmbeddingClient(
        "text-embedding-3-small",
        api_key="sk-test",
        base_url="http://embed.local/v1",
        client=httpx.AsyncClient(transport=httpx.MockTransport(handler)),
    )
    try:
        await client.embed(["a", "b"])
        raise AssertionError("expected count mismatch")
    except ValueError as exc:
        assert "does not match" in str(exc)


async def test_embedding_client_closes_owned_client() -> None:
    client = OpenAIEmbeddingClient("m", api_key="sk-test", base_url="http://embed.local/v1")
    await client.aclose()
    assert client._client.is_closed


async def test_service_close_closes_embedder() -> None:
    class ClosingEmbedder(FakeEmbedder):
        def __init__(self) -> None:
            super().__init__()
            self.closed = False

        async def aclose(self) -> None:
            self.closed = True

    embedder = ClosingEmbedder()
    store = FakeStore()
    service = RetrievalService(embedder, store)
    await service.close()
    assert embedder.closed


def test_non_positive_dimensions_block_before_opening_clients() -> None:
    service, reason = create_retrieval_service(
        make_settings(
            retrieval_database_url="postgresql://db.internal/agent",
            ai_embedding_model="openai:text-embedding-3-small",
            ai_provider_api_key="sk-test",
            ai_embedding_dimensions=0,
        )
    )
    assert service is None
    assert reason == "AI_EMBEDDING_DIMENSIONS must be a positive integer."
    assert reason is not None and "db.internal" not in reason


def test_embedding_spec_with_url_is_not_echoed() -> None:
    settings = make_settings(
        ai_embedding_model="https://user:secret@embed.internal/v1",
        ai_provider_api_key="sk-test",
    )
    client, reason = create_embedding_client(settings)
    assert client is None
    assert reason is not None
    assert "secret" not in reason
    assert "embed.internal" not in reason


class _EmbeddingResult:
    def __init__(self, embeddings: list[list[float]]) -> None:
        self.embeddings = embeddings


class _GoogleEmbedderDouble:
    """替身 Embedder：只记录 document / query 路径，不发请求。"""

    def __init__(self, model: object, *, settings: object = None) -> None:
        self.model = model
        self.settings = settings
        self.document_calls: list[list[str]] = []
        self.query_calls: list[str] = []
        self.document_embeddings: list[list[float]] | None = None
        self.query_embeddings: list[list[float]] | None = None

    async def embed_documents(self, texts: list[str]) -> _EmbeddingResult:
        self.document_calls.append(list(texts))
        embeddings = self.document_embeddings
        if embeddings is None:
            embeddings = [[0.1, 0.2] for _ in texts]
        return _EmbeddingResult(embeddings)

    async def embed_query(self, text: str) -> _EmbeddingResult:
        self.query_calls.append(text)
        embeddings = self.query_embeddings if self.query_embeddings is not None else [[0.3, 0.4]]
        return _EmbeddingResult(embeddings)


def _patch_google_embedding_stack(
    monkeypatch: pytest.MonkeyPatch, *, provider_error: BaseException | None = None
) -> dict[str, object]:
    captured: dict[str, object] = {}

    class _Provider:
        def __init__(self, *, api_key: str | None = None, base_url: str | None = None, **_kwargs: object) -> None:
            if provider_error is not None:
                raise provider_error
            captured["api_key"] = api_key
            captured["base_url"] = base_url

    class _Model:
        def __init__(self, model_name: str, *, provider: object) -> None:
            captured["model_name"] = model_name
            self.provider = provider

    def _embedder(model: object, *, settings: object = None) -> _GoogleEmbedderDouble:
        double = _GoogleEmbedderDouble(model, settings=settings)
        captured["embedder"] = double
        captured["settings"] = settings
        return double

    monkeypatch.setattr("pydantic_ai.providers.google.GoogleProvider", _Provider)
    monkeypatch.setattr("pydantic_ai.embeddings.google.GoogleEmbeddingModel", _Model)
    monkeypatch.setattr("pydantic_ai.embeddings.Embedder", _embedder)
    return captured


def _google_embedding_client(monkeypatch: pytest.MonkeyPatch) -> tuple[GoogleEmbeddingClient, _GoogleEmbedderDouble]:
    captured = _patch_google_embedding_stack(monkeypatch)
    client = GoogleEmbeddingClient(
        "gemini-embedding-001",
        api_key="test-provider-key",
        base_url="https://generativelanguage.example/v1beta",
        dimensions=768,
    )
    embedder = captured["embedder"]
    assert isinstance(embedder, _GoogleEmbedderDouble)
    return client, embedder


async def test_google_embedding_uses_document_and_query_paths(monkeypatch: pytest.MonkeyPatch) -> None:
    client, embedder = _google_embedding_client(monkeypatch)
    assert embedder.settings == {"dimensions": 768}
    assert await client.embed_documents(["alpha", "beta"]) == [[0.1, 0.2], [0.1, 0.2]]
    assert await client.embed_query("lookup") == [0.3, 0.4]
    assert embedder.document_calls == [["alpha", "beta"]]
    assert embedder.query_calls == ["lookup"]


async def test_google_embedding_rejects_count_mismatch(monkeypatch: pytest.MonkeyPatch) -> None:
    client, embedder = _google_embedding_client(monkeypatch)
    embedder.document_embeddings = [[0.1]]
    try:
        await client.embed_documents(["a", "b"])
        raise AssertionError("expected count mismatch")
    except ValueError as exc:
        assert "does not match" in str(exc)

    embedder.query_embeddings = []
    try:
        await client.embed_query("lookup")
        raise AssertionError("expected count mismatch")
    except ValueError as exc:
        assert "does not match" in str(exc)


async def test_google_embedding_rejects_non_finite_values(monkeypatch: pytest.MonkeyPatch) -> None:
    client, embedder = _google_embedding_client(monkeypatch)
    embedder.document_embeddings = [[float("nan")]]
    try:
        await client.embed_documents(["a"])
        raise AssertionError("expected non-finite rejection")
    except ValueError as exc:
        assert "must be numbers" in str(exc)
        assert "nan" not in str(exc).lower()

    embedder.query_embeddings = [[float("inf")]]
    try:
        await client.embed_query("lookup")
        raise AssertionError("expected non-finite rejection")
    except ValueError as exc:
        assert "must be numbers" in str(exc)


def test_create_embedding_client_accepts_google_and_google_gla(monkeypatch: pytest.MonkeyPatch) -> None:
    for spec in ("google:gemini-embedding-001", "google-gla:gemini-embedding-001"):
        captured = _patch_google_embedding_stack(monkeypatch)
        client, reason = create_embedding_client(
            make_settings(
                ai_embedding_model=spec,
                ai_provider_api_key="test-provider-key",
                ai_provider_base_url="https://generativelanguage.example/v1beta",
                ai_embedding_dimensions=768,
            )
        )
        assert reason is None
        assert isinstance(client, GoogleEmbeddingClient)
        assert captured["model_name"] == "gemini-embedding-001"
        assert captured["api_key"] == "test-provider-key"
        assert captured["base_url"] == "https://generativelanguage.example/v1beta"
        assert captured["settings"] == {"dimensions": 768}


def test_create_embedding_client_google_without_credentials_is_blocked(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    secret_key = "sk-test-sentinel"
    secret_url = "https://user:secret@embed.internal/v1"
    _patch_google_embedding_stack(
        monkeypatch,
        provider_error=RuntimeError(f"provider failed {secret_key} {secret_url}"),
    )
    for spec in ("google:gemini-embedding-001", "google-gla:gemini-embedding-001"):
        client, reason = create_embedding_client(
            make_settings(ai_embedding_model=spec, ai_provider_api_key="", ai_provider_base_url="")
        )
        assert client is None
        assert reason == "Embedding provider is not configured or invalid: RuntimeError"
        assert reason is not None
        assert secret_key not in reason
        assert secret_url not in reason
        assert "secret" not in reason
        assert "embed.internal" not in reason


def test_vector_literal_rejects_non_finite() -> None:
    store = PgVectorRetrievalStore("postgresql://unused", dimensions=1)
    try:
        store._vector_literal([float("nan")])
        raise AssertionError("expected non-finite rejection")
    except ValueError as exc:
        assert "nan" not in str(exc).lower()


def test_vector_literal_rejects_wrong_dimension() -> None:
    store = PgVectorRetrievalStore("postgresql://unused", dimensions=3)
    try:
        store._vector_literal([0.1, 0.2])
        raise AssertionError("expected dimension mismatch")
    except ValueError as exc:
        assert "does not match" in str(exc)


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

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return await self.embed(texts)

    async def embed_query(self, text: str) -> list[float]:
        vectors = await self.embed([text])
        if len(vectors) != 1:
            raise ValueError(f"Embedding response count {len(vectors)} does not match input count 1.")
        return vectors[0]


class FakeStore:
    """in-memory store；验证 service 层把 site_id 全链路传入。"""

    def __init__(self) -> None:
        self.docs: dict[tuple[str, str], list[str]] = {}
        self.search_calls: list[str] = []
        self.search_top_ks: list[int] = []

    async def upsert_document(self, *, site_id, source_id, title, chunks, embeddings) -> int:
        assert len(chunks) == len(embeddings)
        self.docs[(site_id, source_id)] = list(chunks)
        return len(chunks)

    async def delete_document(self, *, site_id, source_id) -> None:
        self.docs.pop((site_id, source_id), None)

    async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
        self.search_calls.append(site_id)
        self.search_top_ks.append(top_k)
        results = []
        for (sid, source_id), chunks in self.docs.items():
            if sid != site_id:  # site_id 强制过滤
                continue
            for i, content in enumerate(chunks[:top_k]):
                results.append(
                    RetrievedChunk(source_id=source_id, chunk_index=i, content=content, score=0.9)
                )
        return results[:top_k]

    async def search_lexical(self, *, site_id, tokens, limit) -> list[RetrievedChunk]:
        return []

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
    class FlakyStore(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.calls = 0

        async def search(self, **kwargs):
            self.calls += 1
            if self.calls == 1:
                raise RuntimeError("db down")
            return await super().search(**kwargs)

    service, _ = _service(FlakyStore())
    assert service.degraded is False
    assert service.last_error is None
    assert await service.retrieve_context(site_id="1", query="q") == []
    assert service.degraded is True
    assert service.last_error == "RuntimeError"
    assert await service.retrieve_context(site_id="1", query="q") == []
    assert service.degraded is False
    assert service.last_error is None


# ---------------------------------------------------------------- internal API


def test_retrieval_upsert_hides_database_url() -> None:
    class LeakyStore(FakeStore):
        async def upsert_document(self, **kwargs):
            raise RuntimeError("postgresql://agent:secret@db.internal/agent_retrieval")

    service, _ = _service(LeakyStore())
    response = TestClient(create_app(make_settings(), retrieval_service=service)).put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "sourceId": "kb:1", "title": "t", "content": "hello"},
    )
    body = response.json()
    assert response.status_code == 503
    assert body["message"] == "Retrieval index update failed: RuntimeError"
    assert "secret" not in response.text


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


def test_retrieval_upsert_rejects_oversized_content() -> None:
    response = TestClient(create_app(make_settings())).put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={
            "siteId": "1",
            "sourceId": "kb:1",
            "title": "t",
            "content": "x" * (MAX_CONTENT_LENGTH + 1),
        },
    )
    assert response.status_code == 400
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "BAD_REQUEST"
    assert "content" in body["message"]
    assert "detail" not in body
    assert len(response.content) < 2000


def test_retrieval_upsert_rejects_too_many_chunks() -> None:
    paragraph = "p" * 501
    content = "\n\n".join([paragraph] * (MAX_UPSERT_CHUNKS + 1))
    assert len(content) <= MAX_CONTENT_LENGTH
    assert len(split_text(content)) > MAX_UPSERT_CHUNKS

    service, store = _service()
    response = TestClient(create_app(make_settings(), retrieval_service=service)).put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "sourceId": "kb:1", "title": "t", "content": content},
    )
    assert response.status_code == 400
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "BAD_REQUEST"
    assert "detail" not in body
    assert store.docs == {}


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
    data = response.json()["data"]
    assert data["providerStatus"] == "LIVE"
    assert data["retrievalDegraded"] is False
    health = client.get("/internal/v1/health", headers=TOKEN_HEADER)
    assert health.status_code == 200
    assert health.json()["retrievalDegraded"] is False


def test_chat_reports_retrieval_degraded_without_blocking() -> None:
    class BrokenStore(FakeStore):
        async def search(self, **kwargs):
            raise RuntimeError("db down")

    service, _ = _service(BrokenStore())
    handle = ModelHandle(
        model=TestModel(custom_output_text="answered"), spec="test:model", blocked_reason=None
    )
    client = TestClient(create_app(make_settings(), model_handle=handle, retrieval_service=service))
    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "7", "message": "hello"},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["providerStatus"] == "LIVE"
    assert data["retrievalDegraded"] is True

    health = client.get("/internal/v1/health", headers=TOKEN_HEADER)
    assert health.status_code == 200
    body = health.json()
    assert body["retrievalStatus"] == "LIVE"
    assert body["retrievalDegraded"] is True


async def test_stream_done_always_includes_retrieval_degraded() -> None:
    from agent_services.agent.stream_runtime import stream_chat_events
    from agent_services.schemas.stream import AgentStreamEventType

    async def done_flag(retrieval_degraded: bool) -> bool:
        events = [
            event
            async for event in stream_chat_events(
                TestModel(custom_output_text="ok"),
                site_id="1",
                message="hello",
                retrieval_degraded=retrieval_degraded,
            )
        ]
        done = next(event for event in events if event.type == AgentStreamEventType.DONE)
        return bool(done.data["retrievalDegraded"])

    assert await done_flag(False) is False
    assert await done_flag(True) is True


async def test_service_repeat_upsert_keeps_only_the_latest_document() -> None:
    service, store = _service()
    await service.upsert_document(site_id="1", source_id="kb:1", title="t", content="alpha facts")
    await service.upsert_document(site_id="1", source_id="kb:1", title="t", content="beta facts")
    assert store.docs[("1", "kb:1")] == ["beta facts"]
    hits = await service.search(site_id="1", query="facts")
    assert [hit.content for hit in hits] == ["beta facts"]


def _search_client(service: RetrievalService) -> TestClient:
    return TestClient(create_app(make_settings(), retrieval_service=service))


def test_retrieval_search_returns_hits() -> None:
    service, store = _service()
    client = _search_client(service)
    created = client.put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "sourceId": "kb:1", "title": "t", "content": "alpha facts"},
    )
    assert created.status_code == 200

    response = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "query": "alpha"},
    )
    assert response.status_code == 200
    assert response.json() == {
        "hits": [
            {
                "sourceId": "kb:1",
                "chunkIndex": 0,
                "score": 0.9,
                "content": "alpha facts",
            }
        ],
        "degraded": False,
    }
    assert store.search_top_ks == [DEFAULT_TOP_K]


def test_retrieval_search_degrades_when_embedding_fails() -> None:
    class FailingEmbedder(FakeEmbedder):
        async def embed_query(self, text: str) -> list[float]:
            raise RuntimeError("embed down")

    service = RetrievalService(FailingEmbedder(), FakeStore())
    response = _search_client(service).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "query": "alpha"},
    )
    assert response.status_code == 200
    assert response.json() == {"hits": [], "degraded": True}
    assert service.degraded is True
    assert service.last_error == "RuntimeError"


def test_vector_failure_does_not_return_lexical_hits() -> None:
    class VectorDown(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.lexical_calls = 0

        async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
            raise RuntimeError("db down")

        async def search_lexical(self, *, site_id, tokens, limit) -> list[RetrievedChunk]:
            self.lexical_calls += 1
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    store = VectorDown()
    response = _search_client(RetrievalService(FakeEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "7", "query": "XJ-900", "topK": 4},
    )
    assert response.status_code == 200
    assert response.json() == {"hits": [], "degraded": True}
    assert store.lexical_calls == 0


def test_embedder_failure_does_not_call_lexical_search() -> None:
    class LexicalReady(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.lexical_calls = 0

        async def search_lexical(self, *, site_id, tokens, limit) -> list[RetrievedChunk]:
            self.lexical_calls += 1
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    class FailingEmbedder(FakeEmbedder):
        async def embed_query(self, text: str) -> list[float]:
            raise RuntimeError("embed down")

    store = LexicalReady()
    response = _search_client(RetrievalService(FailingEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "7", "query": "XJ-900", "topK": 4},
    )
    assert response.status_code == 200
    assert response.json() == {"hits": [], "degraded": True}
    assert store.lexical_calls == 0


def test_like_literal_pattern_escapes_wildcards() -> None:
    from agent_services.retrieval.store import (
        lexical_search_statement,
        like_literal_pattern,
    )

    assert like_literal_pattern("100%") == "%100\\%%"
    assert like_literal_pattern("a_b") == "%a\\_b%"
    assert like_literal_pattern("a\\b") == "%a\\\\b%"

    token = "100%_a\\b"
    sql, args = lexical_search_statement("site-7", [token], 20)
    assert args[0] == "site-7"
    assert args[1] == like_literal_pattern(token)
    assert args[2] == 20
    assert "WHERE site_id = $1 AND" in sql
    assert "ESCAPE" in sql
    assert "LIMIT $3" in sql
    assert sql.index("ORDER BY") < sql.index("LIMIT $3")
    assert "DESC, source_id, chunk_index" in sql
    assert "(content ILIKE $2 ESCAPE '\\')::int" in sql
    assert token not in sql
    assert "100%" not in sql

    ranked_sql, ranked_args = lexical_search_statement("site-7", ["XJ-900", "900"], 20)
    assert ranked_args[0] == "site-7"
    assert [item for item in ranked_args if item == "XJ-900" or item == "900"] == []
    assert ranked_sql.index("ORDER BY") < ranked_sql.index("LIMIT $4")
    assert "(content ILIKE $2 ESCAPE '\\')::int" in ranked_sql
    assert "(content ILIKE $3 ESCAPE '\\')::int" in ranked_sql
    assert "XJ-900" not in ranked_sql
    assert "900" not in ranked_sql


def test_retrieval_search_requires_internal_token() -> None:
    response = _search_client(_service()[0]).post(
        "/internal/v1/retrieval/search",
        json={"siteId": "1", "query": "alpha"},
    )
    assert response.status_code == 401
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "UNAUTHORIZED"
    assert "detail" not in body


def test_retrieval_search_blank_query_is_422() -> None:
    response = _search_client(_service()[0]).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "query": "  "},
    )
    assert response.status_code == 422
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "BAD_REQUEST"
    assert "query" in body["message"]
    assert "detail" not in body


def test_retrieval_search_clamps_top_k() -> None:
    service, store = _service()
    response = _search_client(service).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "query": "alpha", "topK": 50},
    )
    assert response.status_code == 200
    assert store.search_top_ks == [20]


def test_retrieval_search_drops_hits_below_min_score() -> None:
    """查询不含代码 token。store 已给出 0.9 与 0.2 时，响应只留 0.9，且不改字段。"""

    class TwoScoreStore(FakeStore):
        async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
            self.search_calls.append(site_id)
            self.search_top_ks.append(top_k)
            return [
                RetrievedChunk("kb:hi", 0, "relevant", 0.9),
                RetrievedChunk("kb:lo", 0, "unrelated", 0.2),
            ]

    store = TwoScoreStore()
    response = _search_client(RetrievalService(FakeEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "query": "hi", "topK": 4},
    )
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"hits", "degraded"}
    assert body == {
        "hits": [
            {
                "sourceId": "kb:hi",
                "chunkIndex": 0,
                "score": 0.9,
                "content": "relevant",
            }
        ],
        "degraded": False,
    }
    assert store.search_top_ks == [4]


async def test_retrieval_min_score_zero_keeps_low_vector_hits() -> None:
    class TwoScoreStore(FakeStore):
        async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
            return [
                RetrievedChunk("kb:hi", 0, "relevant", 0.9),
                RetrievedChunk("kb:lo", 0, "unrelated", 0.2),
            ]

    service = RetrievalService(FakeEmbedder(), TwoScoreStore(), min_score=0)
    hits = await service.search(site_id="1", query="hi", top_k=4)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:hi", 0.9), ("kb:lo", 0.2)]


def test_code_tokens_keep_model_numbers_whole() -> None:
    from agent_services.retrieval.service import code_tokens

    assert code_tokens("XJ-900") == ["XJ-900", "900"]
    assert code_tokens("v1.2") == ["v1.2", "v1"]
    assert code_tokens("M3") == ["M3"]
    assert code_tokens("ABCD") == ["ABCD"]
    assert code_tokens("hi") == []
    assert code_tokens("AI") == []
    assert code_tokens("ab") == []


def test_retrieval_search_surfaces_code_token_below_vector_threshold() -> None:
    """向量通道只有无关 0.9；词法通道带回 XJ-900。topK=1 时只留代码 chunk，score 为覆盖率 1。"""

    class SplitStore(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.lexical_sites: list[str] = []
            self.lexical_tokens: list[list[str]] = []

        async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
            self.search_calls.append(site_id)
            self.search_top_ks.append(top_k)
            return [RetrievedChunk("kb:other", 0, "unrelated prose", 0.9)]

        async def search_lexical(self, *, site_id, tokens, limit) -> list[RetrievedChunk]:
            self.lexical_sites.append(site_id)
            self.lexical_tokens.append(list(tokens))
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    store = SplitStore()
    response = _search_client(RetrievalService(FakeEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"siteId": "7", "query": "XJ-900", "topK": 1},
    )
    assert response.status_code == 200
    assert response.json() == {
        "hits": [
            {
                "sourceId": "kb:code",
                "chunkIndex": 0,
                "score": 1.0,
                "content": "model XJ-900 specs",
            }
        ],
        "degraded": False,
    }
    assert store.lexical_sites == ["7"]
    assert store.lexical_tokens == [["XJ-900", "900"]]


async def test_same_chunk_score_is_max_of_cosine_and_code_coverage() -> None:
    class BothStore(FakeStore):
        async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.4)]

        async def search_lexical(self, *, site_id, tokens, limit) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    service = RetrievalService(FakeEmbedder(), BothStore())
    hits = await service.search(site_id="7", query="XJ-900", top_k=4)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:code", 1.0)]

    class CosineWins(FakeStore):
        async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "only ABCD here", 0.92)]

        async def search_lexical(self, *, site_id, tokens, limit) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "only ABCD here", 0.1)]

    cosine_hits = await RetrievalService(FakeEmbedder(), CosineWins()).search(
        site_id="7", query="ABCD XJ-900", top_k=4
    )
    assert [(hit.source_id, hit.score) for hit in cosine_hits] == [("kb:code", 0.92)]


def test_retrieval_min_score_rejects_out_of_range(monkeypatch: pytest.MonkeyPatch) -> None:
    from pydantic import ValidationError

    from agent_services.config import Settings

    for value in (1.5, -0.01):
        with pytest.raises(RuntimeError, match="RETRIEVAL_MIN_SCORE"):
            create_app(make_settings(retrieval_min_score=value))
    create_app(make_settings(retrieval_min_score=0))
    for literal in ("nope", "true"):
        monkeypatch.setenv("RETRIEVAL_MIN_SCORE", literal)
        with pytest.raises(ValidationError):
            Settings()


_CITATION_HITS = [
    RetrievedChunk("s", 0, "low", 0.1),
    RetrievedChunk("s", 0, "high", 0.95),
    RetrievedChunk("b", 1, "b", 0.5),
    RetrievedChunk("c", 0, "c", 0.4),
    RetrievedChunk("d", 0, "d", 0.3),
    RetrievedChunk("e", 0, "e", 0.2),
    RetrievedChunk("f", 0, "f", 0.15),
]

# 默认门槛 0.35：0.4 留下，0.3 及以下不进 citations。
_EXPECTED_CITATIONS = [
    {"sourceId": "s", "chunkIndex": 0, "score": 0.95},
    {"sourceId": "b", "chunkIndex": 1, "score": 0.5},
    {"sourceId": "c", "chunkIndex": 0, "score": 0.4},
]


class _ManyHitStore(FakeStore):
    async def search(self, *, site_id, embedding, top_k) -> list[RetrievedChunk]:
        self.search_calls.append(site_id)
        self.search_top_ks.append(top_k)
        return list(_CITATION_HITS)


def _live_retrieval_client(service: RetrievalService) -> TestClient:
    handle = ModelHandle(
        model=TestModel(custom_output_text="answered"), spec="test:model", blocked_reason=None
    )
    return TestClient(create_app(make_settings(), model_handle=handle, retrieval_service=service))


def _done_event_data(body: str) -> dict[str, object]:
    for block in body.split("\n\n"):
        lines = [line for line in block.splitlines() if line]
        if not lines or lines[0] != "event: DONE":
            continue
        data = "".join(line.removeprefix("data: ") for line in lines if line.startswith("data: "))
        return json.loads(data)
    raise AssertionError("DONE event missing")


def test_chat_citations_follow_adopted_chunks_and_cap() -> None:
    client = _live_retrieval_client(RetrievalService(FakeEmbedder(), _ManyHitStore()))
    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["retrievalDegraded"] is False
    assert data["citations"] == _EXPECTED_CITATIONS
    assert all("content" not in item for item in data["citations"])


def test_chat_omits_citations_without_grounding() -> None:
    client = _live_retrieval_client(_service()[0])
    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    )
    assert response.status_code == 200
    data = response.json()["data"]
    assert data["retrievalDegraded"] is False
    assert "citations" not in data

    class BrokenStore(FakeStore):
        async def search(self, **kwargs):
            raise RuntimeError("db down")

    degraded = _live_retrieval_client(RetrievalService(FakeEmbedder(), BrokenStore()))
    failed = degraded.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    )
    assert failed.status_code == 200
    body = failed.json()["data"]
    assert body["retrievalDegraded"] is True
    assert "citations" not in body


def test_stream_citations_follow_adopted_chunks_and_cap() -> None:
    client = _live_retrieval_client(RetrievalService(FakeEmbedder(), _ManyHitStore()))
    with client.stream(
        "POST",
        "/internal/v1/chat/stream",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    ) as response:
        assert response.status_code == 200
        done = _done_event_data(response.read().decode("utf-8"))
    assert done["retrievalDegraded"] is False
    assert done["citations"] == _EXPECTED_CITATIONS


def test_stream_omits_citations_without_grounding() -> None:
    client = _live_retrieval_client(_service()[0])
    with client.stream(
        "POST",
        "/internal/v1/chat/stream",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    ) as response:
        done = _done_event_data(response.read().decode("utf-8"))
    assert "citations" not in done

    class BrokenStore(FakeStore):
        async def search(self, **kwargs):
            raise RuntimeError("db down")

    degraded = _live_retrieval_client(RetrievalService(FakeEmbedder(), BrokenStore()))
    with degraded.stream(
        "POST",
        "/internal/v1/chat/stream",
        headers=TOKEN_HEADER,
        json={"siteId": "1", "message": "hello"},
    ) as response:
        failed = _done_event_data(response.read().decode("utf-8"))
    assert failed["retrievalDegraded"] is True
    assert "citations" not in failed
