"""agent_retrieval 派生索引同步契约（Java AgentRetrievalDocument 对应）。"""

from pydantic import BaseModel, Field, field_validator

from agent_services.retrieval.service import DEFAULT_TOP_K
from agent_services.schemas.chat import ModelSelection

MAX_SITE_ID_LENGTH = 64
MAX_SOURCE_ID_LENGTH = 128
MAX_TITLE_LENGTH = 512
# 与 Java 上传侧对齐：文件上限 10MB，抽取文本上限 300 万字符（10MB 纯文本的最坏情形）。
MAX_CONTENT_LENGTH = 3_000_000
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
    # 与 chat 的 model 同一形状。Java 知识检索目前只带 embedding 凭证，缺省时 rerank 用进程 AI_MODEL。
    model_selection: ModelSelection | None = Field(default=None, alias="model")

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


class ReembedPrepareRequest(BaseModel):
    """POST /internal/v1/retrieval/reembed/prepare。model 与 chat / upsert 同一形状。"""

    model_selection: ModelSelection | None = Field(default=None, alias="model")
    confirm_recreate: bool = Field(alias="confirmRecreate")

    model_config = {"populate_by_name": True}


class ReembedPrepareResponse(BaseModel):
    dimensions: int
    column_dimensions: int | None = Field(alias="columnDimensions")
    recreated: bool

    model_config = {"populate_by_name": True}


class RetrievalSearchResponse(BaseModel):
    hits: list[RetrievalSearchHit]
    degraded: bool
    # 只有重排成功才为 true。序列化时 false 不写出，既有响应体仍只有 hits 与 degraded。
    rerank_applied: bool = Field(default=False, alias="rerankApplied")

    model_config = {"populate_by_name": True}
