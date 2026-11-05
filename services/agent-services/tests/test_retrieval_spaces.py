"""Issue #209 UoW-2：分区键 agent_id → base_id（知识空间）的新行为断言。

- 文档只写其所属空间一份（store 层块数对账）；
- 多空间 retrieve 双源命中并按名次合并，命中带 base_id；
- 单空间硬失败跳过、其它空间照常；全部空间失败才整次降级；
- complete 差集清理按（空间, 文档）键，不误删另一空间的同源文档；
- 存量列改名迁移（agent_id → base_id）对 fake conn 的幂等行为；
- 整空间清理与已索引清单两个新内部端点。
"""

from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient
from test_retrieval import FakeEmbedder, FakeStore, _service

from agent_services.app import create_app
from agent_services.retrieval.service import EmbeddingClass
from agent_services.retrieval.store import PgVectorRetrievalStore


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
    hits = await service.retrieve_chunks(base_ids=["10"], query="alpha")
    assert [(hit.source_id, hit.base_id) for hit in hits] == [("kb:1", "10")]


async def test_multi_space_retrieve_merges_by_rank_with_base_ids() -> None:
    """双空间各出一路：rank 1 的两条并列在前，rank 2 的一条在后；每条带自己的 base_id。"""
    service, store = _service()
    await service.upsert_document(base_id="A", source_id="kb:1", title="t", content="alpha one")
    await service.upsert_document(base_id="A", source_id="kb:2", title="t", content="alpha two")
    await service.upsert_document(base_id="B", source_id="kb:3", title="t", content="alpha three")

    hits = await service.retrieve_chunks(base_ids=["A", "B"], query="alpha", top_k=4)
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
    hits = await service.retrieve_chunks(base_ids=["down", "up"], query="alpha")
    assert [hit.source_id for hit in hits] == ["kb:1"]
    assert service.degraded is True

    # 全部空间硬失败：整次降级为空（与单空间失败同口径）。
    hits = await service.retrieve_chunks(base_ids=["down"], query="alpha")
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
        json={"baseIds": ["9"], "query": "alpha"},
    )
    assert hits.json()["hits"] == []
