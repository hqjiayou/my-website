from functools import lru_cache

from pydantic import Field, field_validator
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
    ai_fallback_models: str = Field(default="", validation_alias="AI_FALLBACK_MODELS")

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
    # ignore（默认）：未知 skill id 记入 skillsMissing，不失败
    # reject：请求/默认中含未知 id → 400 BAD_REQUEST
    agent_skills_unknown_policy: str = Field(
        default="ignore", validation_alias="AGENT_SKILLS_UNKNOWN_POLICY"
    )
    agent_enable_todo_tools: bool = Field(default=True, validation_alias="AGENT_ENABLE_TODO_TOOLS")
    # JSON 数组：[{"name":"demo","url":"http://host:port/mcp"}]；缺失 → 不注册 MCP
    mcp_servers_json: str = Field(default="", validation_alias="MCP_SERVERS_JSON")

    # ---- §22 运行预算（每轮；<=0 表示该维度不限）----
    # 防工具循环 / 成本失控；超限 → AGENT_BUDGET_EXCEEDED（诚实失败，不截断伪造回复）
    agent_max_requests: int = Field(default=10, validation_alias="AGENT_MAX_REQUESTS")
    agent_max_tool_calls: int = Field(default=20, validation_alias="AGENT_MAX_TOOL_CALLS")
    agent_max_total_tokens: int = Field(default=0, validation_alias="AGENT_MAX_TOTAL_TOKENS")
    agent_tool_timeout_seconds: float = Field(default=30.0, validation_alias="AGENT_TOOL_TIMEOUT_SECONDS")
    agent_history_max_chars: int = Field(default=24000, validation_alias="AGENT_HISTORY_MAX_CHARS")

    uvicorn_workers: int = Field(default=1, validation_alias="UVICORN_WORKERS")

    @field_validator("agent_skills_unknown_policy", mode="before")
    @classmethod
    def normalize_agent_skills_unknown_policy(cls, value: object) -> object:
        # 启动校验与运行时比较必须看到同一标准形；只在 assert 里 lower 会让 Reject 被当成 ignore。
        if isinstance(value, str):
            return value.strip().lower()
        return value


SKILLS_UNKNOWN_POLICIES = frozenset({"ignore", "reject"})


def assert_skills_unknown_policy(policy: str) -> None:
    """启动期校验：未知 skill 策略只允许 ignore / reject，非法值 fail-fast。"""
    normalized = policy.strip().lower()
    if normalized in SKILLS_UNKNOWN_POLICIES:
        return
    raise RuntimeError(
        f"AGENT_SKILLS_UNKNOWN_POLICY has invalid value {policy!r}. Allowed values: ignore, reject."
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
