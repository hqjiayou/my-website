-- =============================================================================
-- website-services | Flyway V53 | 后台试聊落库（方案 A）：会话表区分来源
-- agent_ai_conversation.source：PORTAL（站点用户端，默认）| ADMIN_PREVIEW（后台预览）。
-- agent_user_id 改可空：预览行无站点用户（NULL）；PORTAL 行非空由 agent-app 写入路径保证。
-- 既有行全部回填为 PORTAL（DEFAULT 回填）。
-- =============================================================================

ALTER TABLE agent_ai_conversation
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'PORTAL';

ALTER TABLE agent_ai_conversation
    ADD CONSTRAINT ck_agent_ai_conversation_source CHECK (source IN ('PORTAL', 'ADMIN_PREVIEW'));

ALTER TABLE agent_ai_conversation
    ALTER COLUMN agent_user_id DROP NOT NULL;

COMMENT ON COLUMN agent_ai_conversation.source IS '会话来源：PORTAL 站点用户端 | ADMIN_PREVIEW 后台试聊';
COMMENT ON COLUMN agent_ai_conversation.agent_user_id IS '站点用户 id；PORTAL 非空（应用层保证），ADMIN_PREVIEW 为 NULL';
