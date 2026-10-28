import math
from functools import lru_cache

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# 余弦相似度门槛的默认值。0 关闭；低于此分的向量命中不返回，也不另取一批凑满 topK。
RETRIEVAL_MIN_SCORE_DEFAULT = 0.35
# 融合后相对分数裁剪的默认比例：融合最高分的此比例以下直接丢弃，不进 topK、不成引用。
# 绝对门槛管「够不够相关」，相对裁剪管「比最佳命中差太远的尾巴」。0 关闭。0.7 由 M11
# eval 黄金集标定：多引案例的弱命中比例为 0.13/0.17/0.61，合法多文档命中比例为 0.99。
RETRIEVAL_SCORE_RATIO_FLOOR_DEFAULT = 0.7
# 每次向量查询在事务内设置的 hnsw.ef_search。维度超过 2000 时不建 HNSW，此值仍会写入。
RETRIEVAL_HNSW_EF_SEARCH_DEFAULT = 40
# 融合候选多于 topK 且能解析聊天模型时，截断前做 listwise 重排。失败则维持融合原序。
RETRIEVAL_RERANK_ENABLED_DEFAULT = True
RETRIEVAL_RERANK_TIMEOUT_SECONDS_DEFAULT = 4.0
RETRIEVAL_RERANK_MAX_CANDIDATES_DEFAULT = 12
# rerank 相关性裁门：开启时 rerank 不再只是重排，而是只保留模型判定相关的候选
# （被省略的候选直接丢弃，不回填），且候选 ≥2 即触发（不再等超过 topK）。
# 2026-10-27 对抗探针标定：干扰文档（错型号 XJ-800、招聘、同型号清洁指南）在
# 分数管线下全部漏过，裁门模式下真模型逐题全对且多文档题两篇都保留。
RETRIEVAL_RERANK_CUTOFF_DEFAULT = True
# 同源块数上限：融合后同一 source_id 最多保留几块进 topK/rerank 窗口。长文档被切成
# 多块时，其相近块会整排挤占 topK、饿死其他文档（2026-10-27 真嵌入探针：保养单题
# top4 全是同一指南的 4 块）。2 兼顾「同文档多面命中」与多样性；0 表示不限制。
RETRIEVAL_MAX_CHUNKS_PER_SOURCE_DEFAULT = 2


class Settings(BaseSettings):
    """环境配置。Provider-neutral：AI_MODEL 使用 Pydantic AI model spec，无默认模型。"""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    port: int = Field(default=3001, validation_alias="PORT")

    # 缺失 → 启动 fail-fast（安全配置错误，非 Provider BLOCKED），见设计 V1 §7
    agent_internal_service_token: str = Field(
        default="", validation_alias="AGENT_INTERNAL_SERVICE_TOKEN"
    )

    # 进程回退模型。Java 在请求里带了模型入口时，这一轮不用这里的 spec / key。
    # Pydantic AI model spec，例如 "openai:gpt-4o-mini" / "azure:..." / "anthropic:..."
    # Google 正式路徑："google:gemini-3-flash-preview"（舊別名 "google-gla:..."）。已實測範例，程式不預設模型。
    # 直配憑證用 AI_PROVIDER_API_KEY；若有 AI_PROVIDER_BASE_URL 也傳給 Google Provider。
    # 未直配金鑰時，Google Provider 讀 GOOGLE_API_KEY 或 GEMINI_API_KEY。
    # 缺失且请求也没带入口 → providerStatus=BLOCKED，chat 503（ADR-0004）
    ai_model: str = Field(default="", validation_alias="AI_MODEL")
    ai_provider_api_key: str = Field(default="", validation_alias="AI_PROVIDER_API_KEY")
    ai_provider_base_url: str = Field(default="", validation_alias="AI_PROVIDER_BASE_URL")
    ai_fallback_models: str = Field(default="", validation_alias="AI_FALLBACK_MODELS")

    # 与 otel.py 同源。Settings 匹配别名时不区分大小写，且别名与字段名不一致时
    # 构造参数 deployment_environment= 会被 extra=ignore 丢掉，所以两个名字都接受。
    deployment_environment: str = Field(
        default="local",
        validation_alias=AliasChoices("OTEL_DEPLOYMENT_ENVIRONMENT", "deployment_environment"),
    )

    # Java↔Python 契约集成测试专用：注入 TestModel，避免真实 Provider 调用
    agent_contract_use_test_model: bool = Field(
        default=False, validation_alias="AGENT_CONTRACT_USE_TEST_MODEL"
    )

    # ---- Phase 3 检索（agent_retrieval 派生索引）----
    # 独立连接串（专用 DB 用户，禁止授予 Java 业务表权限）；缺失 → 检索 BLOCKED
    retrieval_database_url: str = Field(default="", validation_alias="RETRIEVAL_DATABASE_URL")
    # 例如 "openai:text-embedding-3-small" 或 "google:gemini-embedding-001"（舊別名 "google-gla:..."）
    # AI_EMBEDDING_DIMENSIONS 必須與向量欄位一致；gemini-embedding-001 可設 768。此欄位預設仍是 1536。
    # 缺失 → 检索 BLOCKED
    ai_embedding_model: str = Field(default="", validation_alias="AI_EMBEDDING_MODEL")
    ai_embedding_dimensions: int = Field(default=1536, validation_alias="AI_EMBEDDING_DIMENSIONS")
    retrieval_min_score: float = Field(
        default=RETRIEVAL_MIN_SCORE_DEFAULT, validation_alias="RETRIEVAL_MIN_SCORE"
    )
    retrieval_score_ratio_floor: float = Field(
        default=RETRIEVAL_SCORE_RATIO_FLOOR_DEFAULT,
        validation_alias="RETRIEVAL_SCORE_RATIO_FLOOR",
    )
    retrieval_max_chunks_per_source: int = Field(
        default=RETRIEVAL_MAX_CHUNKS_PER_SOURCE_DEFAULT,
        validation_alias="RETRIEVAL_MAX_CHUNKS_PER_SOURCE",
    )
    retrieval_hnsw_ef_search: int = Field(
        default=RETRIEVAL_HNSW_EF_SEARCH_DEFAULT, validation_alias="RETRIEVAL_HNSW_EF_SEARCH"
    )
    retrieval_rerank_enabled: bool = Field(
        default=RETRIEVAL_RERANK_ENABLED_DEFAULT, validation_alias="RETRIEVAL_RERANK_ENABLED"
    )
    retrieval_rerank_timeout_seconds: float = Field(
        default=RETRIEVAL_RERANK_TIMEOUT_SECONDS_DEFAULT,
        validation_alias="RETRIEVAL_RERANK_TIMEOUT_SECONDS",
    )
    retrieval_rerank_max_candidates: int = Field(
        default=RETRIEVAL_RERANK_MAX_CANDIDATES_DEFAULT,
        validation_alias="RETRIEVAL_RERANK_MAX_CANDIDATES",
    )
    retrieval_rerank_cutoff: bool = Field(
        default=RETRIEVAL_RERANK_CUTOFF_DEFAULT,
        validation_alias="RETRIEVAL_RERANK_CUTOFF",
    )

    # ---- Phase 4 业务工具（Function Tools → Java internal API）----
    # Java site-app internal API 根地址；缺失 → 不注册业务工具（纯问答仍可用）
    business_api_base_url: str = Field(default="", validation_alias="BUSINESS_API_BASE_URL")

    # ---- Phase 5 Skills / Todo / MCP ----
    agent_skills_dir: str = Field(default="", validation_alias="AGENT_SKILLS_DIR")
    # 平台 background / guardrails / tool guidance。空则用源码 harness/ 或镜像 /app/harness。
    agent_harness_dir: str = Field(default="", validation_alias="AGENT_HARNESS_DIR")
    agent_default_skills: str = Field(default="orchestration", validation_alias="AGENT_DEFAULT_SKILLS")
    # ignore（默认）：未知 skill id 记入 skillsMissing，不失败
    # reject：请求/默认中含未知 id → 400 BAD_REQUEST
    agent_skills_unknown_policy: str = Field(
        default="ignore", validation_alias="AGENT_SKILLS_UNKNOWN_POLICY"
    )
    # 注入系统提示的 skill 正文总字符上限。必须为正整数；0、负数与非整数字面量启动失败。
    agent_skills_max_instruction_chars: int = Field(
        default=12000, validation_alias="AGENT_SKILLS_MAX_INSTRUCTION_CHARS"
    )
    agent_enable_todo_tools: bool = Field(default=True, validation_alias="AGENT_ENABLE_TODO_TOOLS")
    # 主 run 的 delegate_task。关闭后不注册该工具，harness 也不提委派。
    agent_subagent_enabled: bool = Field(default=True, validation_alias="AGENT_SUBAGENT_ENABLED")
    # 单轮最多委派几次。并行工具调用也算进这一次 run 的次数。
    agent_subagent_max_per_run: int = Field(default=4, validation_alias="AGENT_SUBAGENT_MAX_PER_RUN")
    # 单次子 run 的墙钟超时。到点后工具返回错误字符串，不让主 run 失败。
    agent_subagent_timeout_seconds: float = Field(
        default=60.0, validation_alias="AGENT_SUBAGENT_TIMEOUT_SECONDS"
    )
    # 单次子 run 自己的模型请求上限，与 AGENT_MAX_REQUESTS 分开。
    # 真正能跑多少还要看主 run 当时剩余的请求预算。
    agent_subagent_max_requests: int = Field(default=6, validation_alias="AGENT_SUBAGENT_MAX_REQUESTS")
    # 对话成功后是否再调一次模型抽取长期记忆。关闭后仍可注入请求里已有的 memories。
    # 抽取走独立 Agent，不占用本轮 UsageLimits。
    agent_memory_extraction_enabled: bool = Field(
        default=True, validation_alias="AGENT_MEMORY_EXTRACTION_ENABLED"
    )
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


def assert_retrieval_min_score(value: float) -> None:
    """启动期校验：检索门槛必须落在 0 到 1。0 表示关闭。非法值 fail-fast。"""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RuntimeError("RETRIEVAL_MIN_SCORE must be a number from 0 to 1. 0 disables the threshold.")
    if not math.isfinite(value) or value < 0 or value > 1:
        raise RuntimeError("RETRIEVAL_MIN_SCORE must be a number from 0 to 1. 0 disables the threshold.")


def assert_retrieval_score_ratio_floor(value: float) -> None:
    """启动期校验：相对裁剪比例必须落在 0 到 1。0 表示关闭。非法值 fail-fast。"""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RuntimeError(
            "RETRIEVAL_SCORE_RATIO_FLOOR must be a number from 0 to 1. 0 disables pruning."
        )
    if not math.isfinite(value) or value < 0 or value > 1:
        raise RuntimeError(
            "RETRIEVAL_SCORE_RATIO_FLOOR must be a number from 0 to 1. 0 disables pruning."
        )


def assert_retrieval_max_chunks_per_source(value: int) -> None:
    """启动期校验：同源块数上限必须是非负整数。0 表示不限制。非法值 fail-fast。"""
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise RuntimeError(
            "RETRIEVAL_MAX_CHUNKS_PER_SOURCE must be a non-negative integer. 0 disables the cap."
        )


def assert_skills_max_instruction_chars(value: int) -> None:
    """启动期校验：skill 注入预算必须是正整数，非法值 fail-fast。"""
    if isinstance(value, bool) or not isinstance(value, int) or value < 1:
        raise RuntimeError(
            "AGENT_SKILLS_MAX_INSTRUCTION_CHARS must be a positive integer."
        )


def assert_contract_test_model_allowed(settings: Settings) -> None:
    """契约测试模型只允许本地/测试环境。生产误开会静默用假模型回话。"""
    if not settings.agent_contract_use_test_model:
        return
    environment = settings.deployment_environment.strip().lower()
    if environment in {"local", "test", "ci", "dev", "development"}:
        return
    raise RuntimeError(
        "AGENT_CONTRACT_USE_TEST_MODEL is only allowed in local/test environments "
        "(local, test, ci, dev, development); "
        f"current deployment environment is {settings.deployment_environment!r}."
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
