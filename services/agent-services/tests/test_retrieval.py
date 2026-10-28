"""Phase 3 检索：分块、agent_id 强制过滤、internal 同步端点、chat 检索增强。"""

import json

import anyio
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
    code_tokens,
    create_retrieval_service,
    query_terms,
)
from agent_services.retrieval.store import (
    PgVectorRetrievalStore,
    RetrievedChunk,
    hnsw_index_allowed,
)
from agent_services.schemas.chat import ModelSelection
from agent_services.schemas.retrieval import MAX_CONTENT_LENGTH

# ---------------------------------------------------------------- chunking


def test_split_text_short_content_single_chunk() -> None:
    assert split_text("hello world") == ["hello world"]


def test_split_text_empty_returns_no_chunks() -> None:
    assert split_text("   \n\n  ") == []


def test_split_text_merges_paragraphs_within_limit() -> None:
    chunks = split_text("para one\n\npara two", max_chars=200)
    assert chunks == ["para one\n\npara two"]


def test_split_text_heading_boundaries_prefix_and_stack() -> None:
    content = (
        "# 安装\n\n"
        "安装说明\n\n"
        "补充一句\n\n"
        "## 前置条件\n\n"
        "需要 Python\n\n"
        "### 版本\n\n"
        "3.12 以上\n\n"
        "## 步骤\n\n"
        "第一步\n\n"
        "# 使用\n\n"
        "直接运行"
    )
    chunks = split_text(content, max_chars=400)
    assert chunks == [
        "§ 安装\n# 安装\n\n安装说明\n\n补充一句",
        "§ 安装 > 前置条件\n## 前置条件\n\n需要 Python",
        "§ 安装 > 前置条件 > 版本\n### 版本\n\n3.12 以上",
        "§ 安装 > 步骤\n## 步骤\n\n第一步",
        "§ 使用\n# 使用\n\n直接运行",
    ]
    assert split_text("前文\n# 安装\n正文", max_chars=200) == [
        "前文",
        "§ 安装\n# 安装\n正文",
    ]


def test_split_text_packs_long_single_newline_text_by_line() -> None:
    line = "列表项" + "甲" * 20
    lines = [f"{line}{index}" for index in range(6)]
    content = "\n".join(lines)
    chunks = split_text(content, max_chars=50, overlap=10)
    assert chunks == [
        f"{lines[0]}\n{lines[1]}",
        f"{lines[2]}\n{lines[3]}",
        f"{lines[4]}\n{lines[5]}",
    ]
    for original in lines:
        assert sum(original in chunk.split("\n") for chunk in chunks) == 1


def test_split_text_sentence_boundary_covers_text() -> None:
    content = ("甲" * 40) + "。" + ("乙" * 40) + "！" + ("丙" * 40) + "？"
    max_chars = 70
    chunks = split_text(content, max_chars=max_chars, overlap=10)
    assert chunks
    assert chunks[0][-1] in "。．.!！?？"
    assert len(chunks[0]) < max_chars
    assert all(len(chunk) <= max_chars for chunk in chunks)

    position = 0
    for chunk in chunks:
        start_min = 0 if position == 0 else max(0, position - len(chunk))
        found = content.find(chunk, start_min)
        assert 0 <= found <= position
        position = max(position, found + len(chunk))
    assert position == len(content)

    quoted = ("甲" * 40) + "。”" + ("乙" * 40) + "。"
    quoted_chunks = split_text(quoted, max_chars=70, overlap=8)
    assert quoted_chunks[0].endswith("。”")
    assert all(len(chunk) <= 70 for chunk in quoted_chunks)


def test_split_text_prefix_counts_toward_max_chars() -> None:
    paragraph = "甲" * 20
    content = "# 标题\n\n" + paragraph + "\n\n" + paragraph
    prefix = "§ 标题\n"
    max_chars = len(content) + len(prefix) - 1
    chunks = split_text(content, max_chars=max_chars, overlap=0)
    assert len(chunks) > 1
    assert all(chunk.startswith(prefix) for chunk in chunks)
    assert all(len(chunk) <= max_chars for chunk in chunks)


def test_split_text_rejects_invalid_budget() -> None:
    with pytest.raises(ValueError, match="max_chars must be positive"):
        split_text("hello", max_chars=0)
    with pytest.raises(ValueError, match="overlap must be smaller than max_chars"):
        split_text("hello", max_chars=10, overlap=10)
    assert split_text("   \n\n  ", max_chars=0, overlap=5) == []


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
    """in-memory store；验证 service 层把 agent_id 全链路传入。"""

    def __init__(self) -> None:
        self.docs: dict[tuple[str, str], list[str]] = {}
        self.search_calls: list[str] = []
        self.search_top_ks: list[int] = []
        self.column_dimensions: int | None = None
        self.dimensions: int | None = None
        self.recreate_calls: list[int] = []
        self.ensure_calls: list[int] = []

    async def upsert_document(self, *, agent_id, source_id, title, chunks, embeddings) -> int:
        assert len(chunks) == len(embeddings)
        self.docs[(agent_id, source_id)] = list(chunks)
        return len(chunks)

    async def delete_document(self, *, agent_id, source_id) -> None:
        self.docs.pop((agent_id, source_id), None)

    async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
        self.search_calls.append(agent_id)
        self.search_top_ks.append(top_k)
        results = []
        for (sid, source_id), chunks in self.docs.items():
            if sid != agent_id:  # agent_id 强制过滤
                continue
            for i, content in enumerate(chunks[:top_k]):
                results.append(
                    RetrievedChunk(source_id=source_id, chunk_index=i, content=content, score=0.9)
                )
        return results[:top_k]

    async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
        return []

    async def current_dimensions(self) -> int | None:
        return self.column_dimensions

    async def ensure_schema(self, dimensions: int) -> None:
        self.ensure_calls.append(dimensions)
        self.dimensions = dimensions
        self.column_dimensions = dimensions

    async def recreate_schema(self, new_dimensions: int) -> None:
        self.recreate_calls.append(new_dimensions)
        self.dimensions = new_dimensions

    async def close(self) -> None:
        pass


def _service(store: FakeStore | None = None) -> tuple[RetrievalService, FakeStore]:
    fake_store = store or FakeStore()
    return RetrievalService(FakeEmbedder(), fake_store), fake_store


# ---------------------------------------------------------------- service


async def test_service_upsert_and_site_scoped_search() -> None:
    service, store = _service()
    await service.upsert_document(agent_id="1", source_id="kb:1", title="t", content="alpha facts")
    await service.upsert_document(agent_id="2", source_id="kb:9", title="t", content="beta facts")

    hits_site1 = await service.search(agent_id="1", query="facts")
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
    assert await service.retrieve_context(agent_id="1", query="q") == []
    assert service.degraded is True
    assert service.last_error == "RuntimeError"
    assert await service.retrieve_context(agent_id="1", query="q") == []
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
        json={"agentId": "1", "sourceId": "kb:1", "title": "t", "content": "hello"},
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
        json={"agentId": "1", "sourceId": "kb:1", "title": "t", "content": "hello"},
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
            "agentId": "1",
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
        json={"agentId": "1", "sourceId": "kb:1", "title": "t", "content": content},
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
        json={"agentId": "1", "sourceId": "kb:5", "title": "FAQ", "content": "opening hours 9-18"},
    )
    assert response.status_code == 200
    assert response.json() == {"ok": True, "data": {"chunks": 1}}
    assert ("1", "kb:5") in store.docs

    response = client.request(
        "DELETE",
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        params={"agentId": "1", "sourceId": "kb:5"},
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
        agent_id="1",
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
        json={"agentId": "7", "sourceId": "kb:1", "title": "t", "content": "site seven knowledge"},
    )

    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"agentId": "7", "message": "hello"},
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
        json={"agentId": "7", "message": "hello"},
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
                agent_id="1",
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
    await service.upsert_document(agent_id="1", source_id="kb:1", title="t", content="alpha facts")
    await service.upsert_document(agent_id="1", source_id="kb:1", title="t", content="beta facts")
    assert store.docs[("1", "kb:1")] == ["beta facts"]
    hits = await service.search(agent_id="1", query="facts")
    assert [hit.content for hit in hits] == ["beta facts"]


def _search_client(service: RetrievalService) -> TestClient:
    return TestClient(create_app(make_settings(), retrieval_service=service))


def test_retrieval_search_returns_hits() -> None:
    service, store = _service()
    client = _search_client(service)
    created = client.put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "sourceId": "kb:1", "title": "t", "content": "alpha facts"},
    )
    assert created.status_code == 200

    response = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "query": "alpha"},
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
        json={"agentId": "1", "query": "alpha"},
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

        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            raise RuntimeError("db down")

        async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
            self.lexical_calls += 1
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    store = VectorDown()
    response = _search_client(RetrievalService(FakeEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "7", "query": "XJ-900", "topK": 4},
    )
    assert response.status_code == 200
    assert response.json() == {"hits": [], "degraded": True}
    assert store.lexical_calls == 0


def test_embedder_failure_does_not_call_lexical_search() -> None:
    class LexicalReady(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.lexical_calls = 0

        async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
            self.lexical_calls += 1
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    class FailingEmbedder(FakeEmbedder):
        async def embed_query(self, text: str) -> list[float]:
            raise RuntimeError("embed down")

    store = LexicalReady()
    response = _search_client(RetrievalService(FailingEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "7", "query": "XJ-900", "topK": 4},
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
    assert "WHERE agent_id = $1 AND" in sql
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
        json={"agentId": "1", "query": "alpha"},
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
        json={"agentId": "1", "query": "  "},
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
        json={"agentId": "1", "query": "alpha", "topK": 50},
    )
    assert response.status_code == 200
    assert store.search_top_ks == [20]


def test_retrieval_search_drops_hits_below_min_score() -> None:
    """查询不含代码 token。store 已给出 0.9 与 0.2 时，响应只留 0.9，且不改字段。"""

    class TwoScoreStore(FakeStore):
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            self.search_calls.append(agent_id)
            self.search_top_ks.append(top_k)
            return [
                RetrievedChunk("kb:hi", 0, "relevant", 0.9),
                RetrievedChunk("kb:lo", 0, "unrelated", 0.2),
            ]

    store = TwoScoreStore()
    response = _search_client(RetrievalService(FakeEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "query": "hi", "topK": 4},
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
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            return [
                RetrievedChunk("kb:hi", 0, "relevant", 0.9),
                RetrievedChunk("kb:lo", 0, "unrelated", 0.2),
            ]

    service = RetrievalService(FakeEmbedder(), TwoScoreStore(), min_score=0, score_ratio_floor=0)
    hits = await service.search(agent_id="1", query="hi", top_k=4)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:hi", 0.9), ("kb:lo", 0.2)]


def test_apply_per_source_cap_pure() -> None:
    from agent_services.retrieval.service import apply_per_source_cap

    chunks = [
        RetrievedChunk("kb:a", 0, "a0", 0.9),
        RetrievedChunk("kb:a", 1, "a1", 0.8),
        RetrievedChunk("kb:a", 2, "a2", 0.7),
        RetrievedChunk("kb:b", 0, "b0", 0.6),
    ]
    # 每源最多 2 块：a 的第三块被裁，b 的块补位进来，融合序不变
    assert [(c.source_id, c.chunk_index) for c in apply_per_source_cap(chunks, 2)] == [
        ("kb:a", 0),
        ("kb:a", 1),
        ("kb:b", 0),
    ]
    # 0 = 不限制；上限 ≥ 块数时原样返回
    assert apply_per_source_cap(chunks, 0) == chunks
    assert apply_per_source_cap(chunks, 5) == chunks
    assert apply_per_source_cap([], 2) == []


def test_apply_score_ratio_floor_pure() -> None:
    from agent_services.retrieval.service import apply_score_ratio_floor

    chunks = [
        RetrievedChunk("kb:a", 0, "best", 0.6),
        RetrievedChunk("kb:b", 0, "tail", 0.1),
        RetrievedChunk("kb:c", 0, "close", 0.43),
    ]
    # 0.7 批量门槛 = 0.42：0.1 的尾巴丢弃，0.43 的近分保留
    assert [c.source_id for c in apply_score_ratio_floor(chunks, 0.7)] == ["kb:a", "kb:c"]
    # 关闭时原样返回；单条与空列表不裁剪；最高分 <= 0 不裁剪
    assert apply_score_ratio_floor(chunks, 0) == chunks
    assert apply_score_ratio_floor(chunks[:1], 0.7) == chunks[:1]
    assert apply_score_ratio_floor([], 0.7) == []
    zeros = [RetrievedChunk("kb:z", 0, "z", 0.0), RetrievedChunk("kb:y", 0, "y", 0.0)]
    assert apply_score_ratio_floor(zeros, 0.7) == zeros


async def test_retrieval_search_prunes_relative_tail() -> None:
    """min_score 关闭时相对裁剪仍生效：0.60 的命中在，0.10 的同批尾巴被丢弃。"""

    class TwoScoreStore(FakeStore):
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            return [
                RetrievedChunk("kb:hi", 0, "relevant", 0.6),
                RetrievedChunk("kb:lo", 0, "unrelated", 0.1),
            ]

    service = RetrievalService(FakeEmbedder(), TwoScoreStore(), min_score=0)
    hits = await service.search(agent_id="1", query="hi", top_k=4)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:hi", 0.6)]


async def test_retrieval_search_keeps_close_scores_for_multi_doc() -> None:
    """多文档问题的两条命中分数接近（0.433/0.430）时相对裁剪不误伤。"""

    class TwoScoreStore(FakeStore):
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            return [
                RetrievedChunk("kb:a", 0, "spec", 0.433),
                RetrievedChunk("kb:b", 0, "warranty", 0.430),
            ]

    service = RetrievalService(FakeEmbedder(), TwoScoreStore(), min_score=0)
    hits = await service.search(agent_id="1", query="hi", top_k=4)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:a", 0.433), ("kb:b", 0.430)]


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

        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            self.search_calls.append(agent_id)
            self.search_top_ks.append(top_k)
            return [RetrievedChunk("kb:other", 0, "unrelated prose", 0.9)]

        async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
            self.lexical_sites.append(agent_id)
            self.lexical_tokens.append(list(tokens))
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    store = SplitStore()
    response = _search_client(RetrievalService(FakeEmbedder(), store)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "7", "query": "XJ-900", "topK": 1},
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
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.4)]

        async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.1)]

    service = RetrievalService(FakeEmbedder(), BothStore())
    hits = await service.search(agent_id="7", query="XJ-900", top_k=4)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:code", 1.0)]

    class CosineWins(FakeStore):
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "only ABCD here", 0.92)]

        async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "only ABCD here", 0.1)]

    cosine_hits = await RetrievalService(FakeEmbedder(), CosineWins()).search(
        agent_id="7", query="ABCD XJ-900", top_k=4
    )
    assert [(hit.source_id, hit.score) for hit in cosine_hits] == [("kb:code", 0.92)]


def test_retrieval_score_ratio_floor_rejects_out_of_range() -> None:
    for value in (1.5, -0.01):
        with pytest.raises(RuntimeError, match="RETRIEVAL_SCORE_RATIO_FLOOR"):
            create_app(make_settings(retrieval_score_ratio_floor=value))
    create_app(make_settings(retrieval_score_ratio_floor=0))


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

# 默认门槛 0.35：0.4 留下，0.3 及以下不进 citations。此组用例关掉相对裁剪，只验绝对门槛与上限。
_EXPECTED_CITATIONS = [
    {"sourceId": "s", "chunkIndex": 0, "score": 0.95},
    {"sourceId": "b", "chunkIndex": 1, "score": 0.5},
    {"sourceId": "c", "chunkIndex": 0, "score": 0.4},
]


class _ManyHitStore(FakeStore):
    async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
        self.search_calls.append(agent_id)
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
    client = _live_retrieval_client(
        RetrievalService(FakeEmbedder(), _ManyHitStore(), score_ratio_floor=0)
    )
    response = client.post(
        "/internal/v1/chat",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "message": "hello"},
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
        json={"agentId": "1", "message": "hello"},
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
        json={"agentId": "1", "message": "hello"},
    )
    assert failed.status_code == 200
    body = failed.json()["data"]
    assert body["retrievalDegraded"] is True
    assert "citations" not in body


def test_stream_citations_follow_adopted_chunks_and_cap() -> None:
    client = _live_retrieval_client(
        RetrievalService(FakeEmbedder(), _ManyHitStore(), score_ratio_floor=0)
    )
    with client.stream(
        "POST",
        "/internal/v1/chat/stream",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "message": "hello"},
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
        json={"agentId": "1", "message": "hello"},
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
        json={"agentId": "1", "message": "hello"},
    ) as response:
        failed = _done_event_data(response.read().decode("utf-8"))
    assert failed["retrievalDegraded"] is True
    assert "citations" not in failed


# ---------------------------------------------------------------- lexical v2 / HNSW / rerank


def test_query_terms_cover_cjk_bigrams_and_keep_code_tokens() -> None:
    assert query_terms("什么是退货？") == ["什么", "么是", "是退", "退货"]
    assert query_terms("桜の花") == ["桜の", "の花"]
    assert query_terms("山") == ["山"]
    assert query_terms("XJ-900退货") == ["XJ-900", "900", "退货"]
    segment = "".join(chr(0x4E00 + index) for index in range(30))
    terms = query_terms(segment)
    assert len(terms) == 24
    assert terms[0] == segment[0:2]
    assert terms[-1] == segment[23:25]
    for query in ("XJ-900", "v1.2", "M3", "ABCD"):
        assert query_terms(query) == code_tokens(query)


def test_hnsw_index_allowed_only_within_pgvector_limit() -> None:
    assert hnsw_index_allowed(1536) is True
    assert hnsw_index_allowed(3072) is False


class _LexicalV2Store(FakeStore):
    def __init__(self, chunk: RetrievedChunk) -> None:
        super().__init__()
        self.chunk = chunk
        self.lexical_tokens: list[list[str]] = []

    async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
        self.search_calls.append(agent_id)
        return []

    async def search_lexical(self, *, agent_id, tokens, limit) -> list[RetrievedChunk]:
        self.lexical_tokens.append(list(tokens))
        return [self.chunk]


async def test_chinese_bigram_hits_when_vector_search_is_empty() -> None:
    store = _LexicalV2Store(RetrievedChunk("kb:fuji", 0, "参观富士山", 0.0))
    service = RetrievalService(FakeEmbedder(), store)
    hits = await service.search(agent_id="7", query="富士山", top_k=4)
    assert store.lexical_tokens == [["富士", "士山"]]
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:fuji", 1.0)]
    assert service.degraded is False


async def test_japanese_bigram_hits_when_vector_search_is_empty() -> None:
    store = _LexicalV2Store(RetrievedChunk("kb:tokyo", 0, "東京都庁案内", 0.0))
    service = RetrievalService(FakeEmbedder(), store)
    hits = await service.search(agent_id="7", query="東京都", top_k=4)
    assert store.lexical_tokens == [["東京", "京都"]]
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:tokyo", 1.0)]


_RERANK_HITS = [
    RetrievedChunk("kb:a", 0, "alpha", 0.9),
    RetrievedChunk("kb:b", 0, "beta", 0.8),
    RetrievedChunk("kb:c", 0, "gamma", 0.7),
    RetrievedChunk("kb:d", 0, "delta", 0.6),
]


class _RerankStore(FakeStore):
    async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
        return list(_RERANK_HITS)


def _order_model(payload: str, calls: list[int]) -> FunctionModel:
    def respond(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls.append(1)
        return ModelResponse(parts=[TextPart(payload)])

    return FunctionModel(respond)


async def test_rerank_reorders_candidates_and_marks_applied() -> None:
    calls: list[int] = []
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=_order_model("[3, 1, 4, 2]", calls),
    )
    hits = await service.search(agent_id="1", query="q", top_k=2)
    assert calls == [1]
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:c", 0.7), ("kb:a", 0.9)]
    assert service.rerank_applied is True
    assert service.degraded is False


def test_rerank_response_includes_rerank_applied_when_reordered() -> None:
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=_order_model("[3, 1, 4, 2]", []),
    )
    response = _search_client(service).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "query": "q", "topK": 2},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["degraded"] is False
    assert body["rerankApplied"] is True
    assert [hit["sourceId"] for hit in body["hits"]] == ["kb:c", "kb:a"]


async def test_rerank_model_error_keeps_fused_order() -> None:
    def boom(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        raise RuntimeError("rerank down")

    service = RetrievalService(FakeEmbedder(), _RerankStore(), rerank_model=FunctionModel(boom))
    hits = await service.search(agent_id="1", query="q", top_k=2)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:a", 0.9), ("kb:b", 0.8)]
    assert service.rerank_applied is False
    assert service.degraded is False


def test_rerank_response_omits_flag_when_model_fails() -> None:
    def boom(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        raise RuntimeError("rerank down")

    service = RetrievalService(FakeEmbedder(), _RerankStore(), rerank_model=FunctionModel(boom))
    response = _search_client(service).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"agentId": "1", "query": "q", "topK": 2},
    )
    body = response.json()
    assert "rerankApplied" not in body
    assert body["degraded"] is False
    assert [hit["sourceId"] for hit in body["hits"]] == ["kb:a", "kb:b"]


async def test_rerank_timeout_keeps_fused_order() -> None:
    async def slow(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        await anyio.sleep(1)
        return ModelResponse(parts=[TextPart("[4, 3, 2, 1]")])

    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=FunctionModel(slow),
        rerank_timeout_seconds=0.05,
    )
    hits = await service.search(agent_id="1", query="q", top_k=2)
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:a", 0.9), ("kb:b", 0.8)]
    assert service.rerank_applied is False
    assert service.degraded is False


async def test_rerank_disabled_does_not_call_model() -> None:
    calls: list[int] = []
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=_order_model("[4, 3, 2, 1]", calls),
        rerank_enabled=False,
    )
    hits = await service.search(agent_id="1", query="q", top_k=2)
    assert calls == []
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:a", 0.9), ("kb:b", 0.8)]
    assert service.rerank_applied is False


async def test_rerank_skips_model_when_candidates_fit_in_top_k() -> None:
    """重排模式（裁门关）的旧触发语义：候选不超过 topK 时不调用模型。"""
    calls: list[int] = []
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=_order_model("[4, 3, 2, 1]", calls),
        score_ratio_floor=0,
        rerank_cutoff=False,
    )
    hits = await service.search(agent_id="1", query="q", top_k=4)
    assert calls == []
    assert [hit.source_id for hit in hits] == ["kb:a", "kb:b", "kb:c", "kb:d"]
    assert service.rerank_applied is False


async def test_rerank_prefers_request_model_selection(monkeypatch: pytest.MonkeyPatch) -> None:
    def swap(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart("[2, 1]")])

    def identity(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart("[1, 2]")])

    def fake_entry(**kwargs: object) -> ModelHandle:
        assert kwargs["spec"] == "openai:rerank-test"
        return ModelHandle(model=FunctionModel(swap), spec="openai:rerank-test", blocked_reason=None)

    def fake_process(_settings: object) -> ModelHandle:
        raise AssertionError("process model should not be built when the request selection is live")

    monkeypatch.setattr("agent_services.retrieval.service.create_model_for_entry", fake_entry)
    monkeypatch.setattr("agent_services.retrieval.service.create_model", fake_process)
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=FunctionModel(identity),
        settings=make_settings(),
        rerank_enabled=True,
        rerank_timeout_seconds=4,
    )
    hits = await service.search(
        agent_id="1",
        query="q",
        top_k=1,
        model_selection=ModelSelection(spec="openai:rerank-test", api_key="test-key"),
    )
    assert [hit.source_id for hit in hits] == ["kb:b"]
    assert service.rerank_applied is True


async def test_rerank_uses_process_model_without_selection(monkeypatch: pytest.MonkeyPatch) -> None:
    def swap(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        return ModelResponse(parts=[TextPart("[2, 1]")])

    def fake_process(_settings: object) -> ModelHandle:
        return ModelHandle(model=FunctionModel(swap), spec="test:process", blocked_reason=None)

    monkeypatch.setattr("agent_services.retrieval.service.create_model", fake_process)
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        settings=make_settings(),
        rerank_enabled=True,
        rerank_timeout_seconds=4,
    )
    hits = await service.search(agent_id="1", query="q", top_k=1)
    assert [hit.source_id for hit in hits] == ["kb:b"]
    assert service.rerank_applied is True


def test_parse_rerank_selection_is_strict() -> None:
    from agent_services.retrieval.service import parse_rerank_selection

    assert parse_rerank_selection("[3, 1]", 4) == [2, 0]
    assert parse_rerank_selection("[2, 2, 9]", 4) == [1]
    # 空数组、无数组、只有散文数字都不算有效选择（调用方回退融合原序）
    assert parse_rerank_selection("[]", 4) is None
    assert parse_rerank_selection("candidates 2 and 1", 4) is None
    assert parse_rerank_selection("[1]", 0) is None


async def test_rerank_cutoff_drops_omitted_candidates() -> None:
    """裁门模式：候选数（4）没超过 topK（4）也触发；被省略的候选直接丢弃、不回填。"""
    calls: list[int] = []
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=_order_model("[1, 3]", calls),
        score_ratio_floor=0,
    )
    hits = await service.search(agent_id="1", query="q", top_k=4)
    assert calls == [1]
    assert [hit.source_id for hit in hits] == ["kb:a", "kb:c"]
    assert service.rerank_applied is True


async def test_rerank_cutoff_empty_selection_falls_back_to_fused() -> None:
    """模型回空数组视为不可用：回退融合原序、四条全保留、applied 不置位。"""
    calls: list[int] = []
    service = RetrievalService(
        FakeEmbedder(),
        _RerankStore(),
        rerank_model=_order_model("[]", calls),
        score_ratio_floor=0,
    )
    hits = await service.search(agent_id="1", query="q", top_k=4)
    assert calls == [1]
    assert [hit.source_id for hit in hits] == ["kb:a", "kb:b", "kb:c", "kb:d"]
    assert service.rerank_applied is False


async def test_rerank_cutoff_disabled_keeps_legacy_append() -> None:
    """裁门关时维持重排语义：模型没提到的候选按原序补在后面，不丢弃。"""

    class FiveHitStore(FakeStore):
        async def search(self, *, agent_id, embedding, top_k) -> list[RetrievedChunk]:
            return [
                RetrievedChunk("kb:a", 0, "alpha", 0.9),
                RetrievedChunk("kb:b", 0, "beta", 0.85),
                RetrievedChunk("kb:c", 0, "gamma", 0.8),
                RetrievedChunk("kb:d", 0, "delta", 0.75),
                RetrievedChunk("kb:e", 0, "epsilon", 0.72),
            ]

    calls: list[int] = []
    service = RetrievalService(
        FakeEmbedder(),
        FiveHitStore(),
        rerank_model=_order_model("[2]", calls),
        min_score=0,
        score_ratio_floor=0,
        rerank_cutoff=False,
    )
    hits = await service.search(agent_id="1", query="q", top_k=4)
    # 模型只提了 b；a/c/d 是被按原序补回的（e 同样补回但被 top_k 截掉）
    assert [hit.source_id for hit in hits] == ["kb:b", "kb:a", "kb:c", "kb:d"]
    assert service.rerank_applied is True


# ---------------------------------------------------------------- reembed prepare


class SizedEmbedder:
    def __init__(self, dimensions: int) -> None:
        self.dimensions = dimensions

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [[0.1] * self.dimensions for _ in texts]

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return await self.embed(texts)

    async def embed_query(self, text: str) -> list[float]:
        vectors = await self.embed([text])
        return vectors[0]


def _prepare(service: RetrievalService, body: dict[str, object]):
    return TestClient(create_app(make_settings(), retrieval_service=service)).post(
        "/internal/v1/retrieval/reembed/prepare",
        headers=TOKEN_HEADER,
        json=body,
    )


def test_reembed_prepare_same_dimension_does_not_recreate() -> None:
    store = FakeStore()
    store.column_dimensions = 3
    response = _prepare(RetrievalService(FakeEmbedder(), store), {"confirmRecreate": False})
    assert response.status_code == 200
    assert response.json() == {"dimensions": 3, "columnDimensions": 3, "recreated": False}
    assert store.recreate_calls == []
    assert store.ensure_calls == []


def test_reembed_prepare_dimension_change_without_confirm_is_409() -> None:
    store = FakeStore()
    store.column_dimensions = 3
    response = _prepare(RetrievalService(SizedEmbedder(4), store), {"confirmRecreate": False})
    assert response.status_code == 409
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "DIMENSION_CHANGE_REQUIRED"
    assert body["dimensions"] == 4
    assert body["columnDimensions"] == 3
    assert "providerStatus" not in body
    assert store.recreate_calls == []


def test_reembed_prepare_dimension_change_confirm_recreates() -> None:
    store = FakeStore()
    store.column_dimensions = 3
    response = _prepare(RetrievalService(SizedEmbedder(4), store), {"confirmRecreate": True})
    assert response.status_code == 200
    assert response.json() == {"dimensions": 4, "columnDimensions": 3, "recreated": True}
    assert store.recreate_calls == [4]
    assert store.dimensions == 4
    assert store.ensure_calls == []


def test_reembed_prepare_missing_table_creates_schema() -> None:
    store = FakeStore()
    store.column_dimensions = None
    response = _prepare(RetrievalService(FakeEmbedder(), store), {"confirmRecreate": False})
    assert response.status_code == 200
    assert response.json() == {"dimensions": 3, "columnDimensions": None, "recreated": False}
    assert store.ensure_calls == [3]
    assert store.dimensions == 3
    assert store.recreate_calls == []


def test_reembed_prepare_without_embedder_is_blocked() -> None:
    store = FakeStore()
    response = _prepare(RetrievalService(None, store), {"confirmRecreate": True})
    assert response.status_code == 503
    body = response.json()
    assert body["ok"] is False
    assert body["errorCode"] == "EXTERNAL_DEPENDENCY_BLOCKED"
    assert body["providerStatus"] == "BLOCKED"
    assert "Embedding client is not configured" not in body["message"]
    assert store.recreate_calls == []
    assert store.ensure_calls == []
