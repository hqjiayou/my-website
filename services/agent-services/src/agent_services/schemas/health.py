from pydantic import BaseModel, Field


class McpServerHealth(BaseModel):
    name: str
    enabled: bool = True


class RuntimeLimits(BaseModel):
    """当前生效的单轮运行预算（§22.4/§22.5）；null = 该维度不限。"""

    max_requests: int | None = Field(default=None, alias="maxRequests")
    max_tool_calls: int | None = Field(default=None, alias="maxToolCalls")
    max_total_tokens: int | None = Field(default=None, alias="maxTotalTokens")
    tool_timeout_seconds: float | None = Field(default=None, alias="toolTimeoutSeconds")
    history_max_chars: int | None = Field(default=None, alias="historyMaxChars")

    model_config = {"populate_by_name": True}


class McpReadyServerStatus(BaseModel):
    name: str
    ok: bool
    tool_count: int | None = Field(default=None, alias="toolCount")
    error: str | None = None

    model_config = {"populate_by_name": True}


class McpReadyResponse(BaseModel):
    """GET /internal/v1/mcp/ready。失败时仍带 servers 明细（503 由路由决定）。"""

    ok: bool
    mcp_status: str = Field(alias="mcpStatus")
    servers: list[McpReadyServerStatus] = Field(default_factory=list)
    error_code: str | None = Field(default=None, alias="errorCode")
    message: str | None = None

    model_config = {"populate_by_name": True}


class HealthResponse(BaseModel):
    ok: bool = True
    service: str = "agent-service"
    provider: str
    provider_status: str = Field(alias="providerStatus")
    fallback_providers: list[str] = Field(default_factory=list, alias="fallbackProviders")
    timestamp: str
    mcp_status: str = Field(default="DISABLED", alias="mcpStatus")
    mcp_servers: list[McpServerHealth] = Field(default_factory=list, alias="mcpServers")
    limits: RuntimeLimits = Field(default_factory=RuntimeLimits)
    retrieval_status: str = Field(default="BLOCKED", alias="retrievalStatus")
    retrieval_blocked_reason: str | None = Field(default=None, alias="retrievalBlockedReason")

    model_config = {"populate_by_name": True}
