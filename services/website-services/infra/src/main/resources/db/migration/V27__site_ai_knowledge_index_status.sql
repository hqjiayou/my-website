-- KB 文档派生索引状态：让「已上传但索引失败/未同步」在后台可见、可重试，
-- 取代此前同步失败只写日志、文档仍显示 ACTIVE 的静默滞后。
-- 既有行默认 PENDING（诚实：其索引实际状态未知，重新索引或全量重嵌后落定）。
ALTER TABLE site_ai_knowledge_document
    ADD COLUMN index_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN index_error VARCHAR(500),
    ADD COLUMN indexed_at TIMESTAMPTZ;

COMMENT ON COLUMN site_ai_knowledge_document.index_status IS '派生索引状态：PENDING/INDEXED/FAILED';
COMMENT ON COLUMN site_ai_knowledge_document.index_error IS '上次索引失败原因摘要（消息键或异常类名）';
COMMENT ON COLUMN site_ai_knowledge_document.indexed_at IS '最近一次索引成功时间';
