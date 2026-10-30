-- 一类 embedding spec 塞得进 VARCHAR(256)。多类按分类键记下时要换行拼接，放宽为文本。
-- 已有单值行不改写：比较时把没有换行的旧值当成一类。
ALTER TABLE agent_ai_knowledge_document
    ALTER COLUMN index_embedding_spec TYPE TEXT;

COMMENT ON COLUMN agent_ai_knowledge_document.index_embedding_spec IS
    '所有者分区已成功写入的嵌入类名。多类排序后用换行分隔；单值旧行仍表示当时那一类。NULL 表示未索引过或用了进程默认嵌入模型';
