"""Issue #209 UoW-2：分区键 agent_id → base_id（知识空间）的新行为断言。

- 文档只写其所属空间一份（store 层块数对账）；
- 多空间 retrieve 双源命中并按名次合并，命中带 base_id；
- 单空间硬失败跳过、其它空间照常；全部空间失败才整次降级；
- complete 差集清理按（空间, 文档）键，不误删另一空间的同源文档；
- 存量列改名迁移（agent_id → base_id）对 fake conn 的幂等行为；
- 整空间清理与已索引清单两个新内部端点。

UoW-3 追加：嵌入类集按空间下发（spaces 取代请求级一份类集）。

- 两空间登记不同类集时，各自只用自己的类集命中，串用即无命中；
- search 端点把类集逐空间解析下发（全链）；
- spaces 空/空白/缺失的拒绝口径与旧 baseIds 一致；同 id 空间去重保序。

UoW-4 追加：#202 字面降级按空间口径复核。

- 全部空间嵌入失败时，每空间各自走纯字面降级、双空间仍各出命中，degraded 置位。
"""

from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from test_retrieval import FakeEmbedder, FakeStore, _service

from agent_services.app import create_app
from agent_services.retrieval.service import (
    EmbeddingClass,
    RetrievalService,
    RetrievalSpace,
)
from agent_services.retrieval.store import PgVectorRetrievalStore, RetrievedChunk
from agent_services.schemas.retrieval import RetrievalSearchRequest


async def test_document_written_once_into_its_single_space() -> None:
    """同一文档只属一个空间：写一次就是一份，块数与切块数一致，不存在第二份副本。"""
    service, store = _service()
    count = await service.upsert_document(
        base_id="10", source_id="kb:1", title="t", content="alpha facts"
    )
    assert count == 1
    # store 里只有（空间 10, kb:1）一个条目，总块数 = 单份块数。
    assert list(store.docs) == [("10", "kb:1")]
    assert sum(len(chunks) for chunks in store.docs.values()) == 1
    # 两个 agent 都订阅空间 10 时，读路径仍是这一个空间——不会出现第二份。
    hits = await service.retrieve_chunks(spaces=[RetrievalSpace("10")], query="alpha")
    assert [(hit.source_id, hit.base_id) for hit in hits] == [("kb:1", "10")]


async def test_multi_space_retrieve_merges_by_rank_with_base_ids() -> None:
    """双空间各出一路：rank 1 的两条并列在前，rank 2 的一条在后；每条带自己的 base_id。"""
    service, store = _service()
    await service.upsert_document(base_id="A", source_id="kb:1", title="t", content="alpha one")
    await service.upsert_document(base_id="A", source_id="kb:2", title="t", content="alpha two")
    await service.upsert_document(base_id="B", source_id="kb:3", title="t", content="alpha three")

    hits = await service.retrieve_chunks(
        spaces=[RetrievalSpace("A"), RetrievalSpace("B")], query="alpha", top_k=4
    )
    assert [hit.source_id for hit in hits] == ["kb:1", "kb:3", "kb:2"]
    assert [hit.base_id for hit in hits] == ["A", "B", "A"]
    # 跨空间不重复：每个（空间, 文档, 块）只出现一次。
    keys = [(hit.base_id, hit.source_id, hit.chunk_index) for hit in hits]
    assert len(keys) == len(set(keys))
    assert store.search_calls == ["A", "B"]


async def test_multi_space_skips_hard_failed_space_and_degrades() -> None:
    class OneBaseDown(FakeStore):
        async def search(self, *, base_id, embedding, top_k, embedding_model: str = ""):
            if base_id == "down":
                raise RuntimeError("db down")
            return await super().search(
                base_id=base_id,
                embedding=embedding,
                top_k=top_k,
                embedding_model=embedding_model,
            )

        async def search_lexical(self, *, base_id, tokens, limit, embedding_models=None):
            if base_id == "down":
                raise RuntimeError("db down")
            return await super().search_lexical(
                base_id=base_id,
                tokens=tokens,
                limit=limit,
                embedding_models=embedding_models,
            )

    service, _store = _service(OneBaseDown())
    await service.upsert_document(base_id="up", source_id="kb:1", title="t", content="alpha facts")
    hits = await service.retrieve_chunks(
        spaces=[RetrievalSpace("down"), RetrievalSpace("up")], query="alpha"
    )
    assert [hit.source_id for hit in hits] == ["kb:1"]
    assert service.degraded is True

    # 全部空间硬失败：整次降级为空（与单空间失败同口径）。
    hits = await service.retrieve_chunks(spaces=[RetrievalSpace("down")], query="alpha")
    assert hits == []
    assert service.degraded is True


async def test_complete_prune_is_scoped_to_space_and_document() -> None:
    """complete 差集清理只动（本空间, 本文档）：另一空间的同源文档、异类行不动。"""
    service, store = _service()
    class_x = EmbeddingClass("openai:x", FakeEmbedder())
    class_y = EmbeddingClass("openai:y", FakeEmbedder())
    class_z = EmbeddingClass("openai:z", FakeEmbedder())

    await service.upsert_document(
        base_id="A",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=[class_x],
        prune_stale_classes=True,
    )
    await service.upsert_document(
        base_id="B",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=[class_y],
    )
    assert store.doc_models[("A", "kb:1")] == {"openai:x"}
    assert store.doc_models[("B", "kb:1")] == {"openai:y"}

    # 空间 A 以新完整类集 {z} 重写：A 的 x 行被清理；B 的 y 行必须原样保留。
    await service.upsert_document(
        base_id="A",
        source_id="kb:1",
        title="t",
        content="alpha facts revised",
        embedding_classes=[class_z],
        prune_stale_classes=True,
    )
    assert store.doc_models[("A", "kb:1")] == {"openai:z"}
    assert store.doc_models[("B", "kb:1")] == {"openai:y"}
    # 清理只以（A, kb:1）为键调用过，B 从未成为清理目标。
    assert [(base, source) for base, source, _keep in store.prune_calls] == [
        ("A", "kb:1"),
        ("A", "kb:1"),
    ]

    # fill-missing 子集写（无 complete 标记）绝不清理，跨空间同理。
    await service.upsert_document(
        base_id="B",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=[class_x],
    )
    assert len(store.prune_calls) == 2
    assert store.doc_models[("B", "kb:1")] == {"openai:y", "openai:x"}


async def test_rename_partition_columns_migrates_chunks_and_sheet_rows() -> None:
    class _Conn:
        """记录 SQL 的假连接：chunks 与 sheet_rows 都还带 agent_id 列。"""

        def __init__(self, columns: set[tuple[str, str]]) -> None:
            self.sql: list[str] = []
            self.columns = columns

        async def fetchval(self, sql: str, *args: object):
            if "information_schema.columns" in sql:
                return 1 if (str(args[0]), str(args[1])) in self.columns else None
            if "pg_catalog.pg_class" in sql:
                return 1
            return None

        async def fetch(self, sql: str, *args: object):
            return [{"relname": "chunks"}]

        async def execute(self, sql: str, *args: object) -> None:
            self.sql.append(sql)

    store = PgVectorRetrievalStore("postgresql://unused", dimensions=3)
    conn = _Conn({("chunks", "agent_id"), ("sheet_rows", "agent_id")})
    await store._rename_partition_columns(conn)  # noqa: SLF001 — 与存量迁移测试同法直测
    assert any(
        "ALTER TABLE agent_retrieval.chunks RENAME COLUMN agent_id TO base_id" in sql
        for sql in conn.sql
    )
    assert any(
        "ALTER TABLE agent_retrieval.sheet_rows RENAME COLUMN agent_id TO base_id" in sql
        for sql in conn.sql
    )
    assert any(
        "idx_agent_retrieval_chunks_agent_source" in sql
        and "RENAME TO idx_agent_retrieval_chunks_base_source" in sql
        for sql in conn.sql
    )

    # 幂等：base_id 已存在（已迁移/新库）时一条改名都不发。
    migrated = _Conn({("chunks", "base_id"), ("sheet_rows", "base_id")})
    await store._rename_partition_columns(migrated)  # noqa: SLF001
    assert migrated.sql == []


def test_base_delete_and_indexed_list_endpoints() -> None:
    service, _store = _service()
    client = TestClient(create_app(make_settings(), retrieval_service=service))
    created = client.put(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        json={"baseId": "9", "sourceId": "kb:1", "title": "t", "content": "alpha facts"},
    )
    assert created.status_code == 200

    listed = client.get(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        params={"baseId": "9"},
    )
    assert listed.status_code == 200
    assert listed.json() == {"ok": True, "data": {"baseId": "9", "sourceIds": ["kb:1"]}}

    deleted = client.delete("/internal/v1/retrieval/bases/9", headers=TOKEN_HEADER)
    assert deleted.status_code == 200
    listed = client.get(
        "/internal/v1/retrieval/documents",
        headers=TOKEN_HEADER,
        params={"baseId": "9"},
    )
    assert listed.json()["data"]["sourceIds"] == []
    # 删后检索也无命中（整空间清理落到了每个分区表）。
    hits = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"spaces": [{"baseId": "9"}], "query": "alpha"},
    )
    assert hits.json()["hits"] == []


class _PerClassStore(FakeStore):
    """按（空间, 类）记账的 store：向量 search 只回该空间该类名下写过的文档，
    并记录每次向量检索实际用的 (base_id, embedding_model)。"""

    def __init__(self) -> None:
        super().__init__()
        self.class_searches: list[tuple[str, str]] = []

    async def search(self, *, base_id, embedding, top_k, embedding_model: str = ""):
        self.class_searches.append((base_id, embedding_model))
        results: list[RetrievedChunk] = []
        for (sid, source_id), chunks in self.docs.items():
            if sid != base_id:
                continue
            if embedding_model not in self.doc_models.get((sid, source_id), set()):
                continue
            for i, content in enumerate(chunks[:top_k]):
                results.append(
                    RetrievedChunk(
                        source_id=source_id,
                        chunk_index=i,
                        content=content,
                        score=0.9,
                        base_id=base_id,
                    )
                )
        return results[:top_k]


async def test_each_space_searches_with_its_own_classes() -> None:
    """UoW-3：空间 A 登记类 X、空间 B 登记类 Y，各自只用自己的类集命中。

    A 的文档只有 X 类行、B 的文档只有 Y 类行：双空间检索双源命中，
    且 store 侧断言每空间只被以自己的类名搜过；A 错用 Y 类集时无命中
    （字面通道恒空），证明类集按空间隔离、没有串用。
    """
    service, store = _service(_PerClassStore())
    class_x = EmbeddingClass("openai:x", FakeEmbedder())
    class_y = EmbeddingClass("openai:y", FakeEmbedder())
    await service.upsert_document(
        base_id="A",
        source_id="kb:1",
        title="t",
        content="alpha facts",
        embedding_classes=[class_x],
    )
    await service.upsert_document(
        base_id="B",
        source_id="kb:2",
        title="t",
        content="alpha facts",
        embedding_classes=[class_y],
    )

    hits = await service.retrieve_chunks(
        spaces=[RetrievalSpace("A", [class_x]), RetrievalSpace("B", [class_y])],
        query="alpha",
    )
    assert {(hit.base_id, hit.source_id) for hit in hits} == {("A", "kb:1"), ("B", "kb:2")}
    assert sorted(store.class_searches) == [("A", "openai:x"), ("B", "openai:y")]

    store.class_searches.clear()
    hits = await service.retrieve_chunks(
        spaces=[RetrievalSpace("A", [class_y])], query="alpha"
    )
    assert hits == []
    assert store.class_searches == [("A", "openai:y")]


def test_search_endpoint_applies_classes_per_space(monkeypatch) -> None:
    """端点全链：spaces 里逐空间的 embeddingClasses 被各自解析下发。

    A 的文档只有 X 类行、B 的文档只有 Y 类行；端点按空间下发类集后双源
    命中，store 记录证明 A 只以 X 搜、B 只以 Y 搜。
    """
    service, store = _service(_PerClassStore())

    def fake_create(*, spec: str, api_key: str | None, base_url: str | None, dimensions: int):
        return FakeEmbedder(), None

    monkeypatch.setattr(
        "agent_services.api.internal.create_embedding_client_for", fake_create
    )
    client = TestClient(create_app(make_settings(), retrieval_service=service))
    for base_id, source_id, spec in (("A", "kb:1", "openai:x"), ("B", "kb:2", "openai:y")):
        created = client.put(
            "/internal/v1/retrieval/documents",
            headers=TOKEN_HEADER,
            json={
                "baseId": base_id,
                "sourceId": source_id,
                "title": "t",
                "content": "alpha facts",
                "embeddingClasses": [{"embeddingSpec": spec, "apiKey": "k"}],
            },
        )
        assert created.status_code == 200
    store.class_searches.clear()
    response = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={
            "spaces": [
                {"baseId": "A", "embeddingClasses": [{"embeddingSpec": "openai:x", "apiKey": "ka"}]},
                {"baseId": "B", "embeddingClasses": [{"embeddingSpec": "openai:y", "apiKey": "kb"}]},
            ],
            "query": "alpha",
        },
    )
    assert response.status_code == 200
    hits = response.json()["hits"]
    assert {(hit["baseId"], hit["sourceId"]) for hit in hits} == {
        ("A", "kb:1"),
        ("B", "kb:2"),
    }
    assert sorted(store.class_searches) == [("A", "openai:x"), ("B", "openai:y")]


async def test_empty_spaces_retrieve_returns_nothing() -> None:
    service, _store = _service()
    assert await service.retrieve_chunks(spaces=[], query="alpha") == []
    # 空白 id 在服务层同样被丢弃：结果为空而不是报错、也不置降级。
    assert await service.retrieve_chunks(spaces=[RetrievalSpace("  ")], query="alpha") == []
    assert service.degraded is False


def test_search_request_dedupes_spaces_by_base_id() -> None:
    body = RetrievalSearchRequest.model_validate(
        {
            "spaces": [{"baseId": " 9 "}, {"baseId": "9"}, {"baseId": "7"}],
            "query": "q",
        }
    )
    assert [space.base_id for space in body.spaces] == ["9", "7"]


def test_search_endpoint_rejects_empty_or_blank_spaces() -> None:
    service, _store = _service()
    client = TestClient(create_app(make_settings(), retrieval_service=service))
    empty = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"spaces": [], "query": "alpha"},
    )
    assert empty.status_code == 400
    assert empty.json()["errorCode"] == "BAD_REQUEST"
    # 空白 baseId 被 schema 归一化丢弃，归一化后为空集，同口径 400。
    blank = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"spaces": [{"baseId": "  "}], "query": "alpha"},
    )
    assert blank.status_code == 400
    assert blank.json()["errorCode"] == "BAD_REQUEST"
    # 缺 spaces 字段：校验错误，与旧缺 baseIds 时同为 400 BAD_REQUEST。
    missing = client.post(
        "/internal/v1/retrieval/search",
        headers=TOKEN_HEADER,
        json={"query": "alpha"},
    )
    assert missing.status_code == 400
    assert missing.json()["errorCode"] == "BAD_REQUEST"


async def test_all_spaces_embedding_failure_falls_back_to_lexical_per_space() -> None:
    """全部空间嵌入失败（Issue #202 字面降级按空间口径，UoW-4 复核）：向量通道
    在每个空间内失败，各空间各自降级纯字面兜底，双空间仍各出命中，整次标
    degraded——降级发生在空间内，不是多空间整次落空。"""

    class LexicalStore(FakeStore):
        async def search_lexical(self, *, base_id, tokens, limit, embedding_models=None):
            results = []
            for (sid, source_id), chunks in self.docs.items():
                if sid != base_id:
                    continue
                for index, content in enumerate(chunks):
                    if any(token in content for token in tokens):
                        results.append(
                            RetrievedChunk(
                                source_id=source_id,
                                chunk_index=index,
                                content=content,
                                score=0.1,
                                base_id=base_id,
                            )
                        )
            return results[:limit]

    class QueryDownEmbedder(FakeEmbedder):
        # 只坏查询嵌入：写入（embed_documents）照常，检索时每空间向量通道全灭。
        async def embed_query(self, text: str) -> list[float]:
            raise RuntimeError("embed down")

    service = RetrievalService(QueryDownEmbedder(), LexicalStore())
    await service.upsert_document(base_id="A", source_id="kb:1", title="t", content="alpha one")
    await service.upsert_document(base_id="B", source_id="kb:2", title="t", content="alpha two")

    hits = await service.retrieve_chunks(
        spaces=[RetrievalSpace("A"), RetrievalSpace("B")], query="alpha"
    )
    assert [(hit.base_id, hit.source_id) for hit in hits] == [("A", "kb:1"), ("B", "kb:2")]
    assert service.degraded is True
    assert service.last_error == "EmbeddingError"
