"""agent_retrieval 派生索引同步契约（Java AgentRetrievalDocument 对应）。

分区键是知识空间 baseId（Issue #209 UoW-2 起取代 agentId）：文档只属一个
空间、只写一份；检索请求带空间集 spaces，跨空间由服务层按名次合并。
嵌入登记是空间级的（UoW-3 起）：类集随每个空间下发，不再有请求级的一份
类集套给所有空间。
"""

from pydantic import BaseModel, Field, field_validator

from agent_services.retrieval.embeddings import limit_embedding_class_specs
from agent_services.retrieval.service import DEFAULT_TOP_K
from agent_services.schemas.chat import EmbeddingClassCredentials, ModelSelection

MAX_BASE_ID_LENGTH = 64
MAX_SOURCE_ID_LENGTH = 128
# 一次检索覆盖的空间数上限。agent 订阅的空间远小于此，超限即请求形状不对。
MAX_SEARCH_BASES = 64
MAX_TITLE_LENGTH = 512
# 与 Java 上传侧对齐：文件上限 10MB，抽取文本上限 300 万字符（10MB 纯文本的最坏情形）。
MAX_CONTENT_LENGTH = 3_000_000
# 单次检索返回条数上限；超过则夹到此值，而不是 400。
MAX_SEARCH_TOP_K = 20


class RetrievalSegment(BaseModel):
    """可选索引入库段。缺省时 Python 仍按 content 整篇切块。"""

    text: str = Field(max_length=MAX_CONTENT_LENGTH)
    page: int | None = None
    sheet: str | None = Field(default=None, max_length=256)
    row_start: int | None = Field(default=None, alias="rowStart")
    row_end: int | None = Field(default=None, alias="rowEnd")

    model_config = {"populate_by_name": True}


class RetrievalTableRow(BaseModel):
    """结构化行。rowNumber 是工作表原始行号。"""

    row_number: int = Field(alias="rowNumber")
    cells: list[str] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class RetrievalTable(BaseModel):
    """可选结构化表。缺省时不改切块，并清掉该 source 已入库的结构化行。"""

    sheet: str = Field(max_length=256)
    header_row: int = Field(alias="headerRow")
    headers: list[str] = Field(default_factory=list)
    rows: list[RetrievalTableRow] = Field(default_factory=list)
    truncated: bool = False

    model_config = {"populate_by_name": True}


class RetrievalUpsertRequest(BaseModel):
    base_id: str = Field(alias="baseId", max_length=MAX_BASE_ID_LENGTH)
    source_id: str = Field(alias="sourceId", max_length=MAX_SOURCE_ID_LENGTH)
    title: str = Field(default="", max_length=MAX_TITLE_LENGTH)
    content: str = Field(max_length=MAX_CONTENT_LENGTH)
    embedding_spec: str | None = Field(default=None, alias="embeddingSpec", max_length=256)
    api_key: str | None = Field(default=None, alias="apiKey", max_length=4096)
    base_url: str | None = Field(default=None, alias="baseUrl", max_length=512)
    embedding_classes: list[EmbeddingClassCredentials] | None = Field(
        default=None, alias="embeddingClasses"
    )
    # Issue #202 UoW-2：true 表示本次 embeddingClasses 是该分区的完整登记类集，
    # 写完后可按此集差集清理已注销类的残留行。缺省/false（fill-missing 子集写、
    # 旧版 Java）一律不清理——子集写按差集清理会误删其它类（#126 不变量）。
    embedding_classes_complete: bool = Field(default=False, alias="embeddingClassesComplete")
    segments: list[RetrievalSegment] | None = None
    tables: list[RetrievalTable] | None = None

    model_config = {"populate_by_name": True}

    @field_validator("embedding_classes")
    @classmethod
    def cap_embedding_classes(
        cls, value: list[EmbeddingClassCredentials] | None
    ) -> list[EmbeddingClassCredentials] | None:
        return limit_embedding_class_specs(value)


class RetrievalSearchSpace(BaseModel):
    """一次检索中的一个知识空间（Issue #209 UoW-3）。

    embeddingClasses 是该空间登记的嵌入类集；省略/为空表示该空间未单独登记，
    检索时落请求级 embeddingSpec 嵌入器、再落进程默认类。
    """

    base_id: str = Field(alias="baseId", max_length=MAX_BASE_ID_LENGTH)
    embedding_classes: list[EmbeddingClassCredentials] | None = Field(
        default=None, alias="embeddingClasses"
    )

    model_config = {"populate_by_name": True}

    @field_validator("embedding_classes")
    @classmethod
    def cap_embedding_classes(
        cls, value: list[EmbeddingClassCredentials] | None
    ) -> list[EmbeddingClassCredentials] | None:
        return limit_embedding_class_specs(value)


class RetrievalSearchRequest(BaseModel):
    """POST /internal/v1/retrieval/search。

    query 上限与文档 content 相同（MAX_CONTENT_LENGTH），避免一次检索塞进整库正文。
    topK 缺省为 RetrievalService 的 DEFAULT_TOP_K；大于 20 时夹制到 20。
    embeddingSpec/apiKey/baseUrl 是请求级兜底凭证，只给未带类集的空间用。
    """

    spaces: list[RetrievalSearchSpace] = Field(max_length=MAX_SEARCH_BASES)
    query: str = Field(max_length=MAX_CONTENT_LENGTH)
    top_k: int = Field(default=DEFAULT_TOP_K, alias="topK")
    embedding_spec: str | None = Field(default=None, alias="embeddingSpec", max_length=256)
    api_key: str | None = Field(default=None, alias="apiKey", max_length=4096)
    base_url: str | None = Field(default=None, alias="baseUrl", max_length=512)
    # 与 chat 的 model 同一形状。Java 知识检索目前只带 embedding 凭证，缺省时 rerank 用进程 AI_MODEL。
    model_selection: ModelSelection | None = Field(default=None, alias="model")

    model_config = {"populate_by_name": True}

    @field_validator("spaces")
    @classmethod
    def normalize_spaces(cls, value: list[RetrievalSearchSpace]) -> list[RetrievalSearchSpace]:
        """baseId 去空白、按 baseId 去重、保序；空白 id 的空间丢弃。

        空集是否拒绝由端点回 400（与旧 baseIds 口径一致）。
        """
        normalized: list[RetrievalSearchSpace] = []
        seen: set[str] = set()
        for item in value:
            base_id = item.base_id.strip()
            if base_id and base_id not in seen:
                seen.add(base_id)
                normalized.append(item.model_copy(update={"base_id": base_id}))
        return normalized

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
    # 命中所属知识空间。跨空间检索时调用方据此区分来源；旧数据缺省为空串。
    base_id: str = Field(default="", alias="baseId")

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
