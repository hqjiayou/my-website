"""agent_retrieval 派生索引同步契约（Java AgentRetrievalDocument 对应）。"""

from pydantic import BaseModel, Field

MAX_SITE_ID_LENGTH = 64
MAX_SOURCE_ID_LENGTH = 128
MAX_TITLE_LENGTH = 512
MAX_CONTENT_LENGTH = 200_000


class RetrievalUpsertRequest(BaseModel):
    site_id: str = Field(alias="siteId", max_length=MAX_SITE_ID_LENGTH)
    source_id: str = Field(alias="sourceId", max_length=MAX_SOURCE_ID_LENGTH)
    title: str = Field(default="", max_length=MAX_TITLE_LENGTH)
    content: str = Field(max_length=MAX_CONTENT_LENGTH)

    model_config = {"populate_by_name": True}
