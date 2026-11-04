-- =============================================================================
-- website-services | Flyway V53 | MCP per-agent 选择（Issue #192 UoW-3）
-- 旁表与 agent_ai_prompt_profile 同为 agent 的 1:1 AI 配置：agent_id 即主键。
-- mode 三态（P3 定案）：INHERIT＝用全局启用集（存量默认、无行等价此态）、
-- OFF＝本 agent 明确不用 MCP、CUSTOM＝只用 server_names 里的名字子集
-- （组装时与全局启用集取交集，全局已删/已禁的名字自动失效）。
-- server_names 存 JSON 数组文本，仅 CUSTOM 时有意义，其余态可为空。
-- =============================================================================

CREATE TABLE agent_ai_mcp_selection (
    agent_id     BIGINT PRIMARY KEY,
    mode         VARCHAR(16) NOT NULL DEFAULT 'INHERIT',
    server_names TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    version      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_agent_ai_mcp_selection_mode
        CHECK (mode IN ('INHERIT', 'OFF', 'CUSTOM'))
);

COMMENT ON TABLE agent_ai_mcp_selection IS 'MCP per-agent 选择（#192）：agent 的 1:1 配置，无行＝INHERIT';
COMMENT ON COLUMN agent_ai_mcp_selection.mode IS '三态：INHERIT 用全局启用集／OFF 不用 MCP／CUSTOM 名字子集';
COMMENT ON COLUMN agent_ai_mcp_selection.server_names IS 'CUSTOM 时的 server 名 JSON 数组；与全局启用集取交集生效';

CREATE TRIGGER tr_agent_ai_mcp_selection_touch_updated_at
    BEFORE UPDATE ON agent_ai_mcp_selection
    FOR EACH ROW EXECUTE PROCEDURE fn_touch_updated_at();
