-- 抽取时发现的未求值公式格数量。0 表示没有。不阻断索引。
ALTER TABLE agent_ai_knowledge_document
    ADD COLUMN unevaluated_formula_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN agent_ai_knowledge_document.unevaluated_formula_count IS
    '未求值公式格数量。大于 0 时管理端标记数值可能缺失；不表示索引失败';
