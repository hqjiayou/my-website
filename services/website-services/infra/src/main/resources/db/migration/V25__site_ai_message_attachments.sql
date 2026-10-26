-- =============================================================================
-- website-services | Flyway V25 | 站点 AI 消息图片附件
-- 前置：V16 site_ai_message
-- USER 消息可带 JSON 数组 [{"mediaType":"image/jpeg","data":"<base64>"}]；ASSISTANT 不写
-- =============================================================================

ALTER TABLE site_ai_message ADD COLUMN attachments JSONB;

COMMENT ON COLUMN site_ai_message.attachments IS
    'USER 消息图片附件 JSON 数组（mediaType、data base64）；ASSISTANT 为 NULL';
