-- =============================================================================
-- website-services | Flyway V20 | AI 会话 Todo 编排快照（设计 Agent V1 §21.2）
-- 前置：V16 site_ai_conversation
-- agent-services 每轮返回完整 todos 快照；Java 持久化并在下一轮回传（Python stateless）
-- 只做整体读写、不按字段查询 → TEXT 存 JSON 数组（避免 site 模块引入 PG 专用 TypeHandler）
-- =============================================================================

ALTER TABLE site_ai_conversation ADD COLUMN agent_todos TEXT;

COMMENT ON COLUMN site_ai_conversation.agent_todos IS
    'agent Todo 快照 JSON 数组 [{id,title,status}]；NULL = 无；每轮由 agent-services 返回值整体覆盖';
