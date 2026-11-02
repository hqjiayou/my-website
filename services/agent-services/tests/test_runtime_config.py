"""运行配置热替换与启动拉取（Issue #146 UoW-2）。

钉死定案语义：探活失败不换入且旧对象仍在、stale 版本被拒、正常换入后
app.state 指向新对象且旧的被关闭、清空成未配置态照常换入、维度变更走
DimensionChangeRequired 且 health 诚实 BLOCKED、启动拉取成功收敛 /
失败回退 env 不崩。密钥正文不进响应。
"""

import httpx
import pytest
from conftest import TOKEN_HEADER, make_settings
from fastapi.testclient import TestClient

from agent_services.app import create_app
from agent_services.retrieval import runtime_config
from agent_services.retrieval.runtime_config import (
    sync_runtime_config_from_business_api,
)
from agent_services.retrieval.service import DimensionChangeRequired, RetrievalService

PUSH_URL = "/internal/v1/config/retrieval"
HEALTH_URL = "/internal/v1/health"
NEW_DSN = "postgresql://db2/retrieval"
FULL_PUSH = {
    "retrievalDatabaseUrl": NEW_DSN,
    "aiEmbeddingModel": "google:gemini-embedding-001",
    "aiEmbeddingDimensions": 3,
    "geminiApiKey": "new-secret-key",
    "configVersion": 1,
}


class FakeEmbedder:
    def __init__(self, dimensions: int = 3) -> None:
        self.dimensions = dimensions

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[0.1] * self.dimensions for _ in texts]

    async def embed_query(self, text: str) -> list[float]:
        return [0.1] * self.dimensions


class _FakeConn:
    async def fetchval(self, query: str) -> int:
        return 1


class _Acquire:
    async def __aenter__(self) -> _FakeConn:
        return _FakeConn()

    async def __aexit__(self, *exc: object) -> bool:
        return False


class _FakePool:
    def acquire(self) -> _Acquire:
        return _Acquire()


class FakeRuntimeStore:
    """假 store 工厂的产物：探活走 _get_pool（与 PgVector store 同名入口）。"""

    def __init__(
        self,
        *,
        probe_error: Exception | None = None,
        column_dimensions: int | None = None,
    ) -> None:
        self.probe_error = probe_error
        self.column_dimensions = column_dimensions
        self.probe_calls = 0
        self.closed = False

    async def _get_pool(self) -> _FakePool:
        self.probe_calls += 1
        if self.probe_error is not None:
            raise self.probe_error
        return _FakePool()

    async def current_dimensions(self) -> int | None:
        return self.column_dimensions

    async def search(self, **kwargs: object) -> list:
        raise RuntimeError("vector dimension mismatch")

    async def search_lexical(self, **kwargs: object) -> list:
        return []

    async def close(self) -> None:
        self.closed = True


def _install_factory(monkeypatch, stores: dict[str, FakeRuntimeStore], *, embedder_dimensions: int = 3):
    """把 create_retrieval_service 换成按 DSN 取假 store 的工厂，记录候选 settings。"""
    calls: list = []

    def factory(settings):
        calls.append(settings)
        store = stores[settings.retrieval_database_url]
        service = RetrievalService(FakeEmbedder(embedder_dimensions), store, settings=settings)
        return service, None

    monkeypatch.setattr(runtime_config, "create_retrieval_service", factory)
    return calls


def _health(client: TestClient) -> dict:
    response = client.get(HEALTH_URL, headers=TOKEN_HEADER)
    assert response.status_code == 200
    return response.json()


# ---------------------------------------------------------------- 推送换入


def test_push_applies_and_closes_old_service(monkeypatch) -> None:
    old_store = FakeRuntimeStore()
    old_service = RetrievalService(FakeEmbedder(), old_store)
    app = create_app(make_settings(), retrieval_service=old_service)
    new_store = FakeRuntimeStore()
    calls = _install_factory(monkeypatch, {NEW_DSN: new_store})
    client = TestClient(app)

    response = client.put(PUSH_URL, headers=TOKEN_HEADER, json=FULL_PUSH)

    assert response.status_code == 200
    assert response.json() == {"applied": True, "appliedVersion": 1}
    assert "new-secret-key" not in response.text
    # 新对象换入：state 四件套与版本都指向候选，候选 settings 携带新 key（构造探针）
    assert app.state.retrieval_service is not old_service
    assert app.state.retrieval_service._store is new_store
    assert app.state.settings.gemini_api_key == "new-secret-key"
    assert app.state.settings.retrieval_database_url == NEW_DSN
    assert app.state.runtime_config_version == 1
    assert calls[0].gemini_api_key == "new-secret-key"
    # 探活真实发生过；旧 service 被关闭
    assert new_store.probe_calls == 1
    assert old_store.closed is True


def test_push_requires_internal_token(monkeypatch) -> None:
    _install_factory(monkeypatch, {NEW_DSN: FakeRuntimeStore()})
    client = TestClient(create_app(make_settings()))
    response = client.put(PUSH_URL, json=FULL_PUSH)
    assert response.status_code == 401


def test_push_stale_version_rejected(monkeypatch) -> None:
    new_store = FakeRuntimeStore()
    _install_factory(monkeypatch, {NEW_DSN: new_store})
    app = create_app(make_settings())
    client = TestClient(app)
    assert client.put(PUSH_URL, headers=TOKEN_HEADER, json=FULL_PUSH).json()["applied"] is True
    applied_service = app.state.retrieval_service

    for stale_version in (1, 0):
        response = client.put(
            PUSH_URL,
            headers=TOKEN_HEADER,
            json={**FULL_PUSH, "configVersion": stale_version},
        )
        body = response.json()
        assert body["applied"] is False
        assert body["appliedVersion"] == 1
        assert "Stale" in body["blockedReason"]
    assert app.state.retrieval_service is applied_service
    assert app.state.runtime_config_version == 1


def test_push_probe_failure_keeps_old_config(monkeypatch) -> None:
    good_store = FakeRuntimeStore()
    bad_store = FakeRuntimeStore(probe_error=ConnectionError("refused"))
    _install_factory(monkeypatch, {NEW_DSN: good_store, "postgresql://bad/retrieval": bad_store})
    app = create_app(make_settings())
    client = TestClient(app)
    assert client.put(PUSH_URL, headers=TOKEN_HEADER, json=FULL_PUSH).json()["applied"] is True
    good_service = app.state.retrieval_service
    health_before = _health(client)
    assert health_before["retrievalStatus"] == "LIVE"

    response = client.put(
        PUSH_URL,
        headers=TOKEN_HEADER,
        json={**FULL_PUSH, "retrievalDatabaseUrl": "postgresql://bad/retrieval", "configVersion": 2},
    )

    body = response.json()
    assert body["applied"] is False
    assert body["appliedVersion"] == 1
    assert "connection check failed" in body["blockedReason"]
    # 不换任何 state：旧 service / settings / 版本原样，health 不变；候选被关闭不泄漏
    assert app.state.retrieval_service is good_service
    assert app.state.settings.retrieval_database_url == NEW_DSN
    assert app.state.runtime_config_version == 1
    assert bad_store.closed is True
    assert _health(client)["retrievalStatus"] == "LIVE"


def test_push_unreachable_dsn_with_real_factory_rejected() -> None:
    """不打桩工厂：真 PgVector store 懒连接，探活必须把不可达 DSN 挡下来。"""
    app = create_app(make_settings())
    client = TestClient(app)
    response = client.put(
        PUSH_URL,
        headers=TOKEN_HEADER,
        json={
            "retrievalDatabaseUrl": "postgresql://127.0.0.1:1/nope?connect_timeout=2",
            "aiEmbeddingModel": "openai:text-embedding-3-small",
            "aiEmbeddingDimensions": 1536,
            "geminiApiKey": "k",
            "configVersion": 1,
        },
    )
    body = response.json()
    assert body["applied"] is False
    assert "connection check failed" in body["blockedReason"]
    assert app.state.retrieval_service is None
    assert app.state.runtime_config_version == 0


def test_push_clear_to_unconfigured_applies(monkeypatch) -> None:
    _install_factory(monkeypatch, {NEW_DSN: FakeRuntimeStore()})
    app = create_app(make_settings())
    client = TestClient(app)
    assert client.put(PUSH_URL, headers=TOKEN_HEADER, json=FULL_PUSH).json()["applied"] is True

    response = client.put(PUSH_URL, headers=TOKEN_HEADER, json={"configVersion": 2})

    assert response.json() == {"applied": True, "appliedVersion": 2}
    assert app.state.retrieval_service is None
    assert set(app.state.retrieval_missing_settings) == {
        "RETRIEVAL_DATABASE_URL",
        "AI_EMBEDDING_MODEL",
        "AI_EMBEDDING_DIMENSIONS",
    }
    health = _health(client)
    assert health["retrievalStatus"] == "BLOCKED"
    assert "RETRIEVAL_DATABASE_URL" in health["retrievalBlockedReason"]


# ---------------------------------------------------------------- 维度变更


async def test_dimension_change_blocks_honestly_and_reembed_path_intact(monkeypatch) -> None:
    store = FakeRuntimeStore(column_dimensions=1536)
    _install_factory(monkeypatch, {NEW_DSN: store}, embedder_dimensions=768)
    app = create_app(make_settings())
    client = TestClient(app)

    response = client.put(
        PUSH_URL,
        headers=TOKEN_HEADER,
        json={**FULL_PUSH, "aiEmbeddingDimensions": 768},
    )

    # 维度变更不阻止换入，但状态诚实：health BLOCKED 并点名待重嵌
    assert response.json() == {"applied": True, "appliedVersion": 1}
    assert "re-embedding" in app.state.retrieval_blocked_reason
    health = _health(client)
    assert health["retrievalStatus"] == "BLOCKED"
    assert "1536" in health["retrievalBlockedReason"]

    service = app.state.retrieval_service
    # 索引侧落既有 DimensionChangeRequired 路径（prepare 端点映射 409）
    with pytest.raises(DimensionChangeRequired):
        await service.prepare_reembed()
    # 检索侧维度不匹配收敛成 degraded 空结果，不抛 500 噪声
    chunks = await service.retrieve_chunks(agent_id="1", query="anything")
    assert chunks == []
    assert service.degraded is True


# ---------------------------------------------------------------- 启动拉取


def _pull_transport(payload: dict) -> httpx.MockTransport:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/internal/agent/v1/runtime-config"
        assert request.headers["X-Internal-Service-Token"] == "test-internal-token"
        return httpx.Response(200, json=payload)

    return httpx.MockTransport(handler)


async def test_startup_pull_converges_to_page_config(monkeypatch) -> None:
    new_store = FakeRuntimeStore()
    _install_factory(monkeypatch, {NEW_DSN: new_store})
    app = create_app(make_settings(business_api_base_url="http://java.test"))
    transport = _pull_transport(
        {
            "code": 0,
            "message": "success",
            "data": {
                "configured": True,
                "retrievalDatabaseUrl": NEW_DSN,
                "aiEmbeddingModel": "google:gemini-embedding-001",
                "aiEmbeddingDimensions": 3,
                "geminiApiKey": "pulled-key",
                "configVersion": 7,
            },
        }
    )

    await sync_runtime_config_from_business_api(app, transport=transport)

    assert app.state.runtime_config_version == 7
    assert app.state.settings.gemini_api_key == "pulled-key"
    assert app.state.retrieval_service is not None
    assert app.state.retrieval_service._store is new_store


async def test_startup_pull_unconfigured_keeps_env() -> None:
    app = create_app(make_settings(business_api_base_url="http://java.test"))
    transport = _pull_transport(
        {"code": 0, "message": "success", "data": {"configured": False, "configVersion": 0}}
    )
    await sync_runtime_config_from_business_api(app, transport=transport)
    assert app.state.runtime_config_version == 0
    assert app.state.retrieval_service is None


async def test_startup_pull_failure_keeps_env_and_does_not_raise() -> None:
    app = create_app(make_settings(business_api_base_url="http://java.test"))

    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("java not up", request=request)

    await sync_runtime_config_from_business_api(app, transport=httpx.MockTransport(handler))
    assert app.state.runtime_config_version == 0
    assert app.state.settings.gemini_api_key == ""

    error_transport = httpx.MockTransport(lambda request: httpx.Response(500, json={}))
    await sync_runtime_config_from_business_api(app, transport=error_transport)
    assert app.state.runtime_config_version == 0


async def test_startup_pull_skipped_without_business_base_url() -> None:
    app = create_app(make_settings())
    # business_api_base_url 为空：直接返回，不发请求（transport 为 None 也不会用到）
    await sync_runtime_config_from_business_api(app)
    assert app.state.runtime_config_version == 0


def test_lifespan_triggers_startup_pull(monkeypatch) -> None:
    calls: list = []

    async def fake_sync(app) -> None:
        calls.append(app)

    monkeypatch.setattr("agent_services.app.sync_runtime_config_from_business_api", fake_sync)
    with TestClient(create_app(make_settings())) as client:
        assert client.get("/health").status_code == 200
    assert len(calls) == 1
