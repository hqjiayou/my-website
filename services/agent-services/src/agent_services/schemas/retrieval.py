"""agent_retrieval 派生索引同步契约（Java AgentRetrievalDocument 对应）。"""

from pydantic import BaseModel, Field, field_validator

from agent_services.retrieval.service import DEFAULT_TOP_K

MAX_SITE_ID_LENGTH = 64
MAX_SOURCE_ID_LENGTH = 128
MAX_TITLE_LENGTH = 512
MAX_CONTENT_LENGTH = 200_000
# 单次检索返回条数上限；超过则夹到此值，而不是 400。
MAX_SEARCH_TOP_K = 20


class RetrievalUpsertRequest(BaseModel):
    site_id: str = Field(alias="siteId", max_length=MAX_SITE_ID_LENGTH)
    source_id: str = Field(alias="sourceId", max_length=MAX_SOURCE_ID_LENGTH)
    title: str = Field(default="", max_length=MAX_TITLE_LENGTH)
    content: str = Field(max_length=MAX_CONTENT_LENGTH)
    embedding_spec: str | None = Field(default=None, alias="embeddingSpec", max_length=256)
    api_key: str | None = Field(default=None, alias="apiKey", max_length=4096)
    base_url: str | None = Field(default=None, alias="baseUrl", max_length=512)

    model_config = {"populate_by_name": True}


class RetrievalSearchRequest(BaseModel):
    """POST /internal/v1/retrieval/search。

    query 上限与文档 content 相同（MAX_CONTENT_LENGTH），避免一次检索塞进整库正文。
    topK 缺省为 RetrievalService 的 DEFAULT_TOP_K；大于 20 时夹制到 20。
    """

    site_id: str = Field(alias="siteId", max_length=MAX_SITE_ID_LENGTH)
    query: str = Field(max_length=MAX_CONTENT_LENGTH)
    top_k: int = Field(default=DEFAULT_TOP_K, alias="topK")
    embedding_spec: str | None = Field(default=None, alias="embeddingSpec", max_length=256)
    api_key: str | None = Field(default=None, alias="apiKey", max_length=4096)
    base_url: str | None = Field(default=None, alias="baseUrl", max_length=512)

    model_config = {"populate_by_name": True}

    @field_validator("top_k")
    @classmethod
    def clamp_top_k(cls, value: int) -> int:
        if isinstance(value, bool) or not isinstance(value, int) or value < 1:
            raise ValueError("topK must be a positive integer")
        if value > MAX_SEARCH_TOP_K:
            return MAX_SEARCH_TOP_K
        return value


class RetrievalSearchHit(BaseModel):
    source_id: str = Field(alias="sourceId")
    chunk_index: int = Field(alias="chunkIndex")
    score: float
    content: str

    model_config = {"populate_by_name": True}


class RetrievalSearchResponse(BaseModel):
    hits: list[RetrievalSearchHit]
    degraded: bool

    model_config = {"populate_by_name": True}
