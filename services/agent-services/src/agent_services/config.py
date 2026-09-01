from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """环境配置。Provider-neutral：AI_MODEL 使用 Pydantic AI model spec，无默认模型。"""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    port: int = Field(default=3001, validation_alias="PORT")

    # 缺失 → 启动 fail-fast（安全配置错误，非 Provider BLOCKED），见设计 V1 §7
    agent_internal_service_token: str = Field(
        default="", validation_alias="AGENT_INTERNAL_SERVICE_TOKEN"
    )

    # Pydantic AI model spec，例如 "openai:gpt-4o-mini" / "azure:..." / "anthropic:..."
    # 缺失 → providerStatus=BLOCKED，chat 503（ADR-0004）
    ai_model: str = Field(default="", validation_alias="AI_MODEL")
    ai_provider_api_key: str = Field(default="", validation_alias="AI_PROVIDER_API_KEY")
    ai_provider_base_url: str = Field(default="", validation_alias="AI_PROVIDER_BASE_URL")

    # Java↔Python 契约集成测试专用：注入 TestModel，避免真实 Provider 调用
    agent_contract_use_test_model: bool = Field(
        default=False, validation_alias="AGENT_CONTRACT_USE_TEST_MODEL"
    )

    # ---- Phase 3 检索（agent_retrieval 派生索引）----
    # 独立连接串（专用 DB 用户，禁止授予 Java 业务表权限）；缺失 → 检索 BLOCKED
    retrieval_database_url: str = Field(default="", validation_alias="RETRIEVAL_DATABASE_URL")
    # 例如 "openai:text-embedding-3-small"；缺失 → 检索 BLOCKED
    ai_embedding_model: str = Field(default="", validation_alias="AI_EMBEDDING_MODEL")
    ai_embedding_dimensions: int = Field(default=1536, validation_alias="AI_EMBEDDING_DIMENSIONS")

    # ---- Phase 4 业务工具（Function Tools → Java internal API）----
    # Java site-app internal API 根地址；缺失 → 不注册业务工具（纯问答仍可用）
    business_api_base_url: str = Field(default="", validation_alias="BUSINESS_API_BASE_URL")

    # ---- Phase 5 Skills / Todo / MCP ----
    agent_skills_dir: str = Field(default="", validation_alias="AGENT_SKILLS_DIR")
    agent_default_skills: str = Field(default="orchestration", validation_alias="AGENT_DEFAULT_SKILLS")
    agent_enable_todo_tools: bool = Field(default=True, validation_alias="AGENT_ENABLE_TODO_TOOLS")
    # JSON 数组：[{"name":"demo","url":"http://host:port/mcp"}]；缺失 → 不注册 MCP
    mcp_servers_json: str = Field(default="", validation_alias="MCP_SERVERS_JSON")

    uvicorn_workers: int = Field(default=1, validation_alias="UVICORN_WORKERS")


@lru_cache
def get_settings() -> Settings:
    return Settings()
