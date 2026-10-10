-- =============================================================================
-- website-services | Flyway V21 | AI 消息运行元数据（设计 Agent V1 §22.8）
-- 前置：V16 site_ai_message
-- 仅 ASSISTANT 消息写入：{usage:{requests,toolCalls,inputTokens,outputTokens}, tools:[{name,ok}], skillsApplied:[]}
-- 只做整体读写、不按字段查询 → TEXT 存 JSON（与 V20 agent_todos 一致）；不含工具参数/返回内容
-- =============================================================================

ALTER TABLE site_ai_message ADD COLUMN agent_meta TEXT;

COMMENT ON COLUMN site_ai_message.agent_meta IS
    'Agent 单轮运行元数据 JSON（用量/工具轨迹/生效 skills）；仅 ASSISTANT；NULL = 无或历史数据；仅后台观测';
