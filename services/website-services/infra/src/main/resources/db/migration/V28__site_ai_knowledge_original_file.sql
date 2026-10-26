-- KB 文档原始文件留存：原档存对象存储（S3/MinIO），文档行只留引用与指纹。
-- 此前上传只抽文字、二进制直接丢弃，无法下载原档或重跑转换。
ALTER TABLE site_ai_knowledge_document
    ADD COLUMN source_format VARCHAR(16),
    ADD COLUMN original_filename VARCHAR(255),
    ADD COLUMN storage_bucket VARCHAR(64),
    ADD COLUMN storage_key VARCHAR(512),
    ADD COLUMN file_size_bytes BIGINT,
    ADD COLUMN sha256 VARCHAR(64);

-- 同站点内按内容指纹去重（软删行不占位，查询侧过滤 deleted）
CREATE INDEX idx_kb_document_site_sha256
    ON site_ai_knowledge_document (site_id, sha256);

COMMENT ON COLUMN site_ai_knowledge_document.source_format IS '来源格式（扩展名，如 pdf/docx/xlsx/md）';
COMMENT ON COLUMN site_ai_knowledge_document.storage_key IS '原档对象存储 key；为空表示未保存原档（手工录入或存储未配置）';
COMMENT ON COLUMN site_ai_knowledge_document.sha256 IS '原档 SHA-256（十六进制），同站点去重用';
