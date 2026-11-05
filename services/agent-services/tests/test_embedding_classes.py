"""嵌入模型分库：同类共表、异维另表、检索按名次合并。"""

import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from pydantic import ValidationError
from pydantic_ai.messages import ModelMessage, ModelResponse, TextPart
from pydantic_ai.models.function import AgentInfo, FunctionModel

from agent_services.app import create_app
from agent_services.retrieval.service import RetrievalService
from agent_services.retrieval.store import RetrievedChunk
from agent_services.schemas.chat import ChatRequest
from agent_services.schemas.retrieval import RetrievalSearchRequest


class FakeEmbedder:
    async def embed_query(self, text: str) -> list[float]:
        return [0.1, 0.2, 0.3]

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[0.1, 0.2, 0.3] for _ in texts]


class FakeStore:
    async def upsert_document(
        self, *, base_id, source_id, title, chunks, embeddings, embedding_model: str = ""
    ) -> int:
        return len(chunks)

    async def delete_document(self, *, base_id, source_id) -> None:
        return None

    async def search(
        self, *, base_id, embedding, top_k, embedding_model: str = ""
    ) -> list[RetrievedChunk]:
        return []

    async def search_lexical(
        self, *, base_id, tokens, limit, embedding_models=None
    ) -> list[RetrievedChunk]:
        return []

    async def close(self) -> None:
        return None


def _search_client(service: RetrievalService) -> TestClient:
    return TestClient(create_app(make_settings(), retrieval_service=service))


def test_same_model_name_is_one_class_and_alias_collapses() -> None:
    from agent_services.retrieval.embeddings import embedding_class_name

    assert embedding_class_name(" google:gemini-embedding-001 ") == "google:gemini-embedding-001"
    assert (
        embedding_class_name("google-gla:gemini-embedding-001")
        == "google:gemini-embedding-001"
    )
    assert embedding_class_name("openai:text-embedding-3-small") == "openai:text-embedding-3-small"


def test_new_dimension_gets_its_own_table_and_does_not_drop_others() -> None:
    from agent_services.retrieval.store import (
        LEGACY_CHUNKS_DROP_SQL,
        class_row_delete_sql,
        create_class_table_sql,
        dimension_table_name,
        vector_search_sql,
    )

    assert dimension_table_name(1536, legacy_dimensions=1536) == "chunks"
    assert dimension_table_name(768, legacy_dimensions=1536) == "chunks_d768"
    assert dimension_table_name(768, legacy_dimensions=None) == "chunks"
    assert dimension_table_name(768, legacy_dimensions=768) == "chunks"

    created = create_class_table_sql("chunks_d768", 768)
    assert "chunks_d768" in created
    assert "embedding_model" in created
    assert "vector(768)" in created
    assert "DROP" not in created.upper()

    delete_sql = class_row_delete_sql("chunks")
    assert "embedding_model = $3" in delete_sql
    assert "DROP" not in delete_sql.upper()
    legacy_nulls = class_row_delete_sql("chunks", include_legacy_nulls=True)
    assert "embedding_model IS NULL" in legacy_nulls

    same_dimension = vector_search_sql("chunks")
    other_dimension = vector_search_sql("chunks_d768")
    assert "embedding_model = $3" in same_dimension
    assert "agent_retrieval.chunks_d768" in other_dimension
    assert "DROP" not in other_dimension.upper()
    # 重建只动遗留表，不动其它维度的类表。
    assert LEGACY_CHUNKS_DROP_SQL == "DROP TABLE IF EXISTS agent_retrieval.chunks"
    assert "chunks_d" not in LEGACY_CHUNKS_DROP_SQL

    with pytest.raises(ValueError):
        vector_search_sql("chunks;drop table chunks")


def test_rank_merge_sums_places_and_ignores_cosine_values() -> None:
    from agent_services.retrieval.service import merge_by_rank

    merged = merge_by_rank(
        [
            [
                RetrievedChunk("kb:second", 0, "high cosine", 0.99),
                RetrievedChunk("kb:also", 0, "next", 0.90),
            ],
            [RetrievedChunk("kb:first", 0, "low cosine", 0.41)],
        ]
    )
    assert [item.source_id for item in merged] == ["kb:first", "kb:second", "kb:also"]
    assert merged[0].score == pytest.approx(1.0)
    assert merged[1].score == pytest.approx(1.0)
    assert merged[2].score == pytest.approx(0.5)


def test_legacy_search_request_omits_embedding_classes() -> None:
    body = RetrievalSearchRequest.model_validate({"baseIds": ["1"], "query": "hours"})
    assert body.embedding_classes is None
    chat = ChatRequest.model_validate({"agentId": "1", "message": "hi"})
    assert chat.embedding_classes is None


def test_embedding_class_cap_is_four_after_alias_dedup() -> None:
    four = [
        {"embeddingSpec": "google-gla:gemini-embedding-001", "apiKey": "a"},
        {"embeddingSpec": "google:gemini-embedding-001", "apiKey": "b"},
        {"embeddingSpec": "openai:text-embedding-3-small", "apiKey": "c"},
        {"embeddingSpec": "openai:text-embedding-3-large", "apiKey": "d"},
        {"embeddingSpec": "openai:embed-extra", "apiKey": "e"},
    ]
    # google 与 google-gla 是同一类，去重后仍是 4 类。
    kept = RetrievalSearchRequest.model_validate(
        {"baseIds": ["1"], "query": "q", "embeddingClasses": four}
    )
    assert kept.embedding_classes is not None
    assert len(kept.embedding_classes) == 4
    assert kept.embedding_classes[0].embedding_spec == "google-gla:gemini-embedding-001"

    fifth = four + [{"embeddingSpec": "openai:one-more", "apiKey": "f"}]
    with pytest.raises(ValidationError):
        RetrievalSearchRequest.model_validate(
            {"baseIds": ["1"], "query": "q", "embeddingClasses": fifth}
        )


def test_search_endpoint_rejects_more_than_four_classes() -> None:
    service, _store = RetrievalService(FakeEmbedder(), FakeStore()), None
    classes = [{"embeddingSpec": f"openai:m{index}", "apiKey": "k"} for index in range(5)]
    response = _search_client(service).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"baseIds": ["1"], "query": "q", "embeddingClasses": classes},
    )
    assert response.status_code == 400
    assert response.json()["errorCode"] == "BAD_REQUEST"


class _ClassEmbedder:
    def __init__(self, marker: float, *, fail: bool = False) -> None:
        self.marker = marker
        self.fail = fail
        self.queries: list[str] = []

    async def embed_query(self, text: str) -> list[float]:
        self.queries.append(text)
        if self.fail:
            raise RuntimeError("embed down")
        return [self.marker, 0.0]

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[self.marker, 0.0] for _ in texts]


class _ClassStore(FakeStore):
    def __init__(self, hits: dict[str, list[RetrievedChunk]]) -> None:
        super().__init__()
        self.hits = hits
        self.models: list[str] = []
        self.lexical_calls = 0

    async def search(
        self, *, base_id, embedding, top_k, embedding_model: str = ""
    ) -> list[RetrievedChunk]:
        self.models.append(embedding_model)
        return list(self.hits.get(embedding_model, []))

    async def search_lexical(
        self, *, base_id, tokens, limit, embedding_models=None
    ) -> list[RetrievedChunk]:
        self.lexical_calls += 1
        return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.0)]


def _classes(*pairs: tuple[str, _ClassEmbedder]):
    from agent_services.retrieval.service import EmbeddingClass

    return [EmbeddingClass(name, client) for name, client in pairs]


async def test_two_classes_merge_by_rank_then_rerank() -> None:
    """类间不比余弦。名次合成后仍走重排，重排可以把名次第一换成第二名。"""
    from agent_services.retrieval.service import EmbeddingClass

    store = _ClassStore(
        {
            "openai:a": [RetrievedChunk("kb:second", 0, "alpha", 0.99)],
            "openai:b": [RetrievedChunk("kb:first", 0, "beta", 0.40)],
        }
    )
    calls: list[int] = []

    def swap(_messages: list[ModelMessage], _info: AgentInfo) -> ModelResponse:
        calls.append(1)
        return ModelResponse(parts=[TextPart("[2, 1]")])

    service = RetrievalService(
        None,
        store,
        min_score=0,
        score_ratio_floor=0,
        rerank_model=FunctionModel(swap),
        rerank_cutoff=False,
    )
    hits = await service.search(
        base_ids=["1"],
        query="q",
        top_k=1,
        embedding_classes=[
            EmbeddingClass("openai:a", _ClassEmbedder(0.1)),
            EmbeddingClass("openai:b", _ClassEmbedder(0.2)),
        ],
    )
    assert calls == [1]
    assert [hit.source_id for hit in hits] == ["kb:second"]
    assert service.rerank_applied is True
    assert store.models == ["openai:a", "openai:b"]


async def test_relative_trim_stays_inside_each_class() -> None:
    """A 类的 0.10 在类内被相对裁剪丢掉；B 类的 0.48 不跟 A 类的 0.90 比。"""
    store = _ClassStore(
        {
            "openai:a": [
                RetrievedChunk("kb:top", 0, "top", 0.90),
                RetrievedChunk("kb:tail", 0, "tail", 0.10),
            ],
            "openai:b": [
                RetrievedChunk("kb:mid", 0, "mid", 0.50),
                RetrievedChunk("kb:near", 0, "near", 0.48),
            ],
        }
    )
    service = RetrievalService(None, store, min_score=0, score_ratio_floor=0.7, rerank_enabled=False)
    hits = await service.retrieve_chunks(
        base_ids=["1"],
        query="q",
        top_k=4,
        embedding_classes=_classes(
            ("openai:a", _ClassEmbedder(0.1)),
            ("openai:b", _ClassEmbedder(0.2)),
        ),
    )
    assert [hit.source_id for hit in hits] == ["kb:mid", "kb:top", "kb:near"]
    assert service.degraded is False


async def test_rank_score_is_not_passed_through_absolute_threshold() -> None:
    """名次分 1/3 低于 0.35，但该类余弦已经过门槛，合成时不再按绝对分丢掉。"""
    store = _ClassStore(
        {
            "openai:a": [
                RetrievedChunk("kb:r1", 0, "r1", 0.90),
                RetrievedChunk("kb:r2", 0, "r2", 0.80),
                RetrievedChunk("kb:r3", 0, "r3", 0.70),
            ],
            "openai:b": [RetrievedChunk("kb:other", 0, "other", 0.50)],
        }
    )
    service = RetrievalService(
        None, store, min_score=0.35, score_ratio_floor=0.7, rerank_enabled=False
    )
    hits = await service.retrieve_chunks(
        base_ids=["1"],
        query="q",
        top_k=4,
        embedding_classes=_classes(
            ("openai:a", _ClassEmbedder(0.1)),
            ("openai:b", _ClassEmbedder(0.2)),
        ),
    )
    assert "kb:r3" in [hit.source_id for hit in hits]


async def test_one_class_keeps_current_cosine_and_coverage_score() -> None:
    class Both(FakeStore):
        async def search(
            self, *, base_id, embedding, top_k, embedding_model: str = ""
        ) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.4)]

        async def search_lexical(
            self, *, base_id, tokens, limit, embedding_models=None
        ) -> list[RetrievedChunk]:
            return [RetrievedChunk("kb:code", 0, "model XJ-900 specs", 0.0)]

    service = RetrievalService(None, Both(), rerank_enabled=False)
    hits = await service.search(
        base_ids=["7"],
        query="XJ-900",
        top_k=4,
        embedding_classes=_classes(("openai:a", _ClassEmbedder(0.1))),
    )
    assert [(hit.source_id, hit.score) for hit in hits] == [("kb:code", 1.0)]


async def test_one_class_embed_failure_falls_back_to_lexical_and_degrades() -> None:
    """Issue #202 UoW-3：单类嵌入失败降级纯字面，命中返回且 degraded 为真。"""
    store = _ClassStore({})
    service = RetrievalService(None, store, rerank_enabled=False)
    hits = await service.retrieve_chunks(
        base_ids=["7"],
        query="XJ-900",
        embedding_classes=_classes(("openai:a", _ClassEmbedder(0.1, fail=True))),
    )
    assert [hit.source_id for hit in hits] == ["kb:code"]
    assert service.degraded is True
    assert store.lexical_calls == 1


async def test_partial_class_failure_keeps_other_classes_and_lexical() -> None:
    store = _ClassStore({"openai:a": [RetrievedChunk("kb:vec", 0, "vector hit", 0.8)]})
    service = RetrievalService(None, store, min_score=0, score_ratio_floor=0, rerank_enabled=False)
    hits = await service.retrieve_chunks(
        base_ids=["7"],
        query="XJ-900",
        top_k=4,
        embedding_classes=_classes(
            ("openai:a", _ClassEmbedder(0.1)),
            ("openai:b", _ClassEmbedder(0.2, fail=True)),
        ),
    )
    assert service.degraded is True
    assert {hit.source_id for hit in hits} == {"kb:vec", "kb:code"}
    assert store.lexical_calls == 1


async def test_all_class_embed_failures_fall_back_to_lexical() -> None:
    """Issue #202 UoW-3：多类全部失败与单类同口径——纯字面兜底，不整次落空。"""
    store = _ClassStore({"openai:a": [RetrievedChunk("kb:vec", 0, "vector hit", 0.8)]})
    service = RetrievalService(None, store, rerank_enabled=False)
    hits = await service.retrieve_chunks(
        base_ids=["7"],
        query="XJ-900",
        embedding_classes=_classes(
            ("openai:a", _ClassEmbedder(0.1, fail=True)),
            ("openai:b", _ClassEmbedder(0.2, fail=True)),
        ),
    )
    assert [hit.source_id for hit in hits] == ["kb:code"]
    assert service.degraded is True
    assert store.lexical_calls == 1


async def test_upsert_of_one_class_does_not_remove_another() -> None:
    class Memory(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.kept: dict[tuple[str, str, str], list[str]] = {}

        async def upsert_document(
            self, *, base_id, source_id, title, chunks, embeddings, embedding_model: str = ""
        ) -> int:
            assert embeddings
            self.kept[(base_id, source_id, embedding_model)] = list(chunks)
            return len(chunks)

    store = Memory()
    service = RetrievalService(None, store)
    await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=_classes(("openai:a", _ClassEmbedder(0.1))),
    )
    await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=_classes(("openai:b", _ClassEmbedder(0.2))),
    )
    assert ("1", "kb:1", "openai:a") in store.kept
    assert ("1", "kb:1", "openai:b") in store.kept


class _PruningStore(FakeStore):
    """按 (agent, source, model) 记行的 store，实现差集清理供 UoW-2 验证。"""

    def __init__(self) -> None:
        super().__init__()
        self.kept: dict[tuple[str, str, str], list[str]] = {}
        self.prune_calls: list[list[str]] = []

    async def upsert_document(
        self, *, base_id, source_id, title, chunks, embeddings, embedding_model: str = ""
    ) -> int:
        self.kept[(base_id, source_id, embedding_model)] = list(chunks)
        return len(chunks)

    async def prune_document_classes(self, *, base_id, source_id, keep_models) -> None:
        self.prune_calls.append(list(keep_models))
        keep = set(keep_models)
        for key in [k for k in self.kept if k[0] == base_id and k[1] == source_id]:
            if key[2] not in keep:
                del self.kept[key]


async def test_complete_upsert_prunes_deregistered_class_rows() -> None:
    """Issue #202 UoW-2：完整类集重写后，已注销类的旧行被差集清理（防复活）。"""
    store = _PruningStore()
    service = RetrievalService(None, store)
    await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=_classes(
            ("openai:a", _ClassEmbedder(0.1)), ("openai:b", _ClassEmbedder(0.2))
        ),
        prune_stale_classes=True,
    )
    assert ("1", "kb:1", "openai:b") in store.kept
    # 类 B 被注销：以仅 A 的完整类集改正文重写，B 的旧行必须消失。
    await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="t",
        content="alpha facts revised",
        embedding_classes=_classes(("openai:a", _ClassEmbedder(0.1))),
        prune_stale_classes=True,
    )
    assert ("1", "kb:1", "openai:b") not in store.kept
    assert store.kept[("1", "kb:1", "openai:a")] == ["alpha facts revised"]
    assert store.prune_calls[-1] == ["openai:a"]


async def test_subset_upsert_without_complete_flag_never_prunes() -> None:
    """fill-missing 子集写（缺省 flag）绝不触发清理——#126 不变量。"""
    store = _PruningStore()
    service = RetrievalService(None, store)
    await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=_classes(("openai:a", _ClassEmbedder(0.1))),
    )
    await service.upsert_document(
        base_id="1",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=_classes(("openai:b", _ClassEmbedder(0.2))),
    )
    assert store.prune_calls == []
    assert ("1", "kb:1", "openai:a") in store.kept
    assert ("1", "kb:1", "openai:b") in store.kept


def test_prune_delete_sql_is_difference_only() -> None:
    from agent_services.retrieval.store import prune_delete_sql

    sql = prune_delete_sql("chunks_d768")
    assert "agent_retrieval.chunks_d768" in sql
    assert "NOT (embedding_model = ANY($3::text[]))" in sql
    assert "sheet_rows" not in sql
    with pytest.raises(ValueError):
        prune_delete_sql("chunks;drop table chunks")


def test_search_endpoint_uses_each_registered_class(monkeypatch: pytest.MonkeyPatch) -> None:
    created: dict[str, _ClassEmbedder] = {}

    def fake_create(*, spec: str, api_key: str | None, base_url: str | None, dimensions: int):
        client = _ClassEmbedder(0.3 if "small" in spec else 0.7)
        created[spec] = client
        return client, None

    monkeypatch.setattr(
        "agent_services.api.internal.create_embedding_client_for", fake_create
    )
    store = _ClassStore(
        {
            "openai:text-embedding-3-small": [
                RetrievedChunk("kb:second", 0, "alpha", 0.99)
            ],
            "google:gemini-embedding-001": [RetrievedChunk("kb:first", 0, "beta", 0.20)],
        }
    )
    service = RetrievalService(FakeEmbedder(), store, min_score=0, score_ratio_floor=0, rerank_enabled=False)
    response = _search_client(service).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={
            "baseIds": ["1"],
            "query": "q",
            "topK": 2,
            "embeddingClasses": [
                {"embeddingSpec": "openai:text-embedding-3-small", "apiKey": "sk-a"},
                {
                    "embeddingSpec": "google-gla:gemini-embedding-001",
                    "apiKey": "sk-b",
                },
            ],
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["degraded"] is False
    # 0.99 的余弦不是第一名；名次并列时按 sourceId。
    assert [hit["sourceId"] for hit in body["hits"]] == ["kb:first", "kb:second"]
    assert store.models == ["openai:text-embedding-3-small", "google:gemini-embedding-001"]
    assert created["openai:text-embedding-3-small"].queries == ["q"]
    assert created["google-gla:gemini-embedding-001"].queries == ["q"]


def test_legacy_single_embedding_request_still_searches_one_class(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def fake_create(*, spec: str, api_key: str | None, base_url: str | None, dimensions: int):
        return _ClassEmbedder(0.5), None

    monkeypatch.setattr(
        "agent_services.api.internal.create_embedding_client_for", fake_create
    )
    store = _ClassStore(
        {"openai:text-embedding-3-small": [RetrievedChunk("kb:only", 0, "alpha", 0.9)]}
    )
    service = RetrievalService(FakeEmbedder(), store, min_score=0, score_ratio_floor=0, rerank_enabled=False)
    response = TestClient(create_app(make_settings(), retrieval_service=service)).post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={
            "baseIds": ["1"],
            "query": "q",
            "embeddingSpec": "openai:text-embedding-3-small",
            "apiKey": "sk-old",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["degraded"] is False
    assert [hit["sourceId"] for hit in body["hits"]] == ["kb:only"]
    assert store.models == ["openai:text-embedding-3-small"]


def test_lexical_statement_filters_requested_classes_only() -> None:
    from agent_services.retrieval.store import lexical_search_statement

    sql, args = lexical_search_statement(
        "site-7",
        ["XJ-900"],
        20,
        "chunks",
        embedding_models=["openai:a"],
        include_legacy_nulls=True,
    )
    assert "embedding_model = ANY($3::text[])" in sql
    assert "embedding_model IS NULL" in sql
    assert args[2] == ["openai:a"]
    assert args[3] == 20
    assert "LIMIT $4" in sql

    other_table, other_args = lexical_search_statement(
        "site-7",
        ["XJ-900"],
        20,
        "chunks_d768",
        embedding_models=["openai:a"],
    )
    assert "agent_retrieval.chunks_d768" in other_table
    assert "embedding_model IS NULL" not in other_table
    assert other_args[2] == ["openai:a"]

    unfiltered, _ = lexical_search_statement("site-7", ["XJ-900"], 20)
    assert "embedding_model" not in unfiltered


def test_lexical_merge_keeps_a_later_table_when_it_scores_higher() -> None:
    from agent_services.retrieval.store import merge_lexical_candidates

    first_table = [
        RetrievedChunk("kb:early", 0, "early", 1.0),
        RetrievedChunk("kb:filler", 0, "filler", 1.0),
    ]
    later_table = [RetrievedChunk("kb:later", 0, "later", 5.0)]
    merged = merge_lexical_candidates([first_table, later_table], limit=2)
    assert [item.source_id for item in merged] == ["kb:later", "kb:early"]


async def test_lexical_search_receives_only_the_requested_classes() -> None:
    class Recording(FakeStore):
        def __init__(self) -> None:
            super().__init__()
            self.lexical_models: list[str] | None = None

        async def search(
            self, *, base_id, embedding, top_k, embedding_model: str = ""
        ) -> list[RetrievedChunk]:
            return []

        async def search_lexical(
            self, *, base_id, tokens, limit, embedding_models=None
        ) -> list[RetrievedChunk]:
            self.lexical_models = list(embedding_models or [])
            return []

    store = Recording()
    service = RetrievalService(None, store, rerank_enabled=False)
    await service.search(
        base_ids=["7"],
        query="XJ-900",
        embedding_classes=_classes(
            ("openai:a", _ClassEmbedder(0.1)),
            ("openai:b", _ClassEmbedder(0.2, fail=True)),
        ),
    )
    assert store.lexical_models == ["openai:a", "openai:b"]
