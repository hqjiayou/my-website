-- =============================================================================
-- website-services | Flyway V35 | 模型入口双模式归属（Portal 双模式切换）
-- mode：KEY = 直连 API key；CLI = 经 CLI 代理池（CLIProxyAPI / Sub2API 等）。
-- 存量入口均为直连 key，默认 KEY。默认位唯一性改为「每个作用域 × 每个模式一个默认」：
-- 原部分唯一索引（平台一个默认、每站一个默认）替换为带 mode 的版本，
-- 否则 KEY / CLI 两种模式无法各自拥有默认入口。
-- =============================================================================

ALTER TABLE agent_model_entry
    ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'KEY';

COMMENT ON COLUMN agent_model_entry.mode IS 'KEY=直连 API key；CLI=经 CLI 代理池';

DROP INDEX IF EXISTS uq_ai_model_entry_platform_default;
DROP INDEX IF EXISTS uq_ai_model_entry_agent_default;

CREATE UNIQUE INDEX uq_agent_model_entry_platform_default
    ON agent_model_entry (mode)
    WHERE deleted = FALSE AND is_default = TRUE AND agent_id IS NULL;

CREATE UNIQUE INDEX uq_agent_model_entry_scoped_default
    ON agent_model_entry (agent_id, mode)
    WHERE deleted = FALSE AND is_default = TRUE AND agent_id IS NOT NULL;
