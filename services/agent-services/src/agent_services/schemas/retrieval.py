"""agent_retrieval 派生索引同步契约（Java AgentRetrievalDocument 对应）。"""

from pydantic import BaseModel, Field


class RetrievalUpsertRequest(BaseModel):
    site_id: str = Field(alias="siteId")
    source_id: str = Field(alias="sourceId")
    title: str = ""
    content: str

    model_config = {"populate_by_name": True}
