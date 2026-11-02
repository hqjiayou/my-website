-- =============================================================================
-- website-services | Flyway V47 | agent-services 运行配置页面化（Issue #146 UoW-1）
-- 平台单行表：检索 DSN／嵌入模型／嵌入维度／Gemini key 四项从 .env 搬到页面配置。
-- 两个密钥字段（retrieval_database_url、gemini_api_key）存 ModelEntryCipher 密文。
-- 单行约束落 DB 层：主键固定 id=1 + CHECK (id = 1)，并预置一行空配置
-- （config_version=0 表示「页面从未保存过」，供 Python 启动拉取区分未配置与拉取失败）。
-- 配置版本列名 config_version：审计基类已有 version 乐观锁列，不可撞名。
-- last_push_* 只记推送结果（保存是真相源，推送失败不回滚保存）。
-- =============================================================================

CREATE TABLE agent_runtime_config (
    id                      BIGINT PRIMARY KEY,
    retrieval_database_url  TEXT,
    ai_embedding_model      VARCHAR(256),
    ai_embedding_dimensions INTEGER,
    gemini_api_key          TEXT,
    config_version          BIGINT NOT NULL DEFAULT 0,
    last_push_status        VARCHAR(16),
    last_push_message       VARCHAR(512),
    last_pushed_at          TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              BIGINT,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              BIGINT,
    deleted                 BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at              TIMESTAMPTZ,
    version                 INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_agent_runtime_config_singleton CHECK (id = 1),
    CONSTRAINT ck_agent_runtime_config_push_status
        CHECK (last_push_status IS NULL OR last_push_status IN ('APPLIED', 'BLOCKED', 'FAILED')),
    CONSTRAINT ck_agent_runtime_config_dimensions
        CHECK (ai_embedding_dimensions IS NULL OR ai_embedding_dimensions > 0),
    CONSTRAINT ck_agent_runtime_config_version CHECK (config_version >= 0)
);

COMMENT ON TABLE agent_runtime_config IS 'agent-services 运行配置（#146）：平台单行，id 固定为 1';
COMMENT ON COLUMN agent_runtime_config.retrieval_database_url IS '检索库 DSN（ModelEntryCipher 密文）；对外只回脱敏形 scheme://host:port/db';
COMMENT ON COLUMN agent_runtime_config.ai_embedding_model IS '嵌入模型 spec（如 google:gemini-embedding-001）';
COMMENT ON COLUMN agent_runtime_config.ai_embedding_dimensions IS '嵌入维度；变更将触发全量重嵌（reembed 链）';
COMMENT ON COLUMN agent_runtime_config.gemini_api_key IS 'Gemini API key（ModelEntryCipher 密文），进程级嵌入回退 key';
COMMENT ON COLUMN agent_runtime_config.config_version IS '配置版本：每次有效保存 +1；0＝从未保存。Python 侧以此防旧推送覆盖（stale 拒绝）';
COMMENT ON COLUMN agent_runtime_config.last_push_status IS '最近一次推送结果：APPLIED／BLOCKED／FAILED';
COMMENT ON COLUMN agent_runtime_config.last_push_message IS '最近一次推送的简短说明（不得含 DSN／key 正文）';
COMMENT ON COLUMN agent_runtime_config.last_pushed_at IS '最近一次推送时间';

CREATE TRIGGER tr_agent_runtime_config_touch_updated_at
    BEFORE UPDATE ON agent_runtime_config
    FOR EACH ROW EXECUTE PROCEDURE fn_touch_updated_at();

-- 预置单行空配置（不含任何真实配置值）：省掉运行时 get-or-insert 的并发竞态。
INSERT INTO agent_runtime_config (id) VALUES (1);
