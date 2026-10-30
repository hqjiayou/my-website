-- 记录索引成功时实际用的 embedding spec，用于检出「索引模型已变更」（stale）。
-- 可空、不回填：存量行语义未知，保持 NULL；新行按 recordIndexOutcome 的规则落值
-- （INDEXED 写当次所有者分区 selection 的 spec，selection 为 null 时写 NULL；
-- FAILED 不覆盖；PENDING 清空）。
ALTER TABLE agent_ai_knowledge_document
    ADD COLUMN index_embedding_spec VARCHAR(256);

COMMENT ON COLUMN agent_ai_knowledge_document.index_embedding_spec IS
    '最近一次成功写入所有者分区时所用的 embedding spec；NULL 表示未索引过或用了进程默认嵌入模型';
