"""运行配置推送契约（Issue #146 UoW-2）。

Java admin 保存页面配置后 PUT /internal/v1/config/retrieval，四项＋configVersion。
四项均可缺席：Java 以 NON_NULL 序列化，缺席（或显式 null）＝该项未配置，
Python 按「未配置」语义收敛，而不是保留旧值（保留原值是 Java 落库侧的语义）。
DSN 与 Gemini key 是明文密钥：只进内存与 Settings 副本，禁止写日志、禁止回显。
"""

from pydantic import BaseModel, Field

from agent_services.retrieval.runtime_config import RuntimeConfigValues


class RuntimeConfigPushRequest(BaseModel):
    retrieval_database_url: str | None = Field(default=None, alias="retrievalDatabaseUrl")
    ai_embedding_model: str | None = Field(default=None, alias="aiEmbeddingModel")
    ai_embedding_dimensions: int | None = Field(default=None, alias="aiEmbeddingDimensions")
    gemini_api_key: str | None = Field(default=None, alias="geminiApiKey")
    config_version: int = Field(alias="configVersion")

    model_config = {"populate_by_name": True}

    def to_values(self) -> RuntimeConfigValues:
        return RuntimeConfigValues(
            config_version=self.config_version,
            retrieval_database_url=self.retrieval_database_url,
            ai_embedding_model=self.ai_embedding_model,
            ai_embedding_dimensions=self.ai_embedding_dimensions,
            gemini_api_key=self.gemini_api_key,
        )


class RuntimeConfigPushResponse(BaseModel):
    """只回结果与版本；被拒原因不含 DSN / key 正文。"""

    applied: bool
    applied_version: int = Field(alias="appliedVersion")
    blocked_reason: str | None = Field(default=None, alias="blockedReason")

    model_config = {"populate_by_name": True}
