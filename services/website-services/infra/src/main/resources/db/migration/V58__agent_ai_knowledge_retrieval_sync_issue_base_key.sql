-- =============================================================================
-- website-services | Flyway V58 | 检索同步问题表换键（Issue #209 UoW-2）
-- 向量分区键由 agent 改为知识空间（base）：同步问题行的唯一键随之由
-- (agent_id, source_id, op) 改为 (base_id, source_id, op)。
--   1. 新增 base_id 列；能经 source_id 找到文档行的（文档为软删、行仍在），
--      回填 base_id = 文档.base_id。agent_id 列保留作留痕（落行时文档所属
--      空间的创建者），不再是键。
--   2. op=UPSERT 行全删：UPSERT 是链接分区扇出滞后的记账，UoW-2 起同步层
--      改为按 base 单写、链接分区不复存在，这类行整体退役；个别文档若真有
--      spec 漂移，stale 检出（index_embedding_spec 与空间登记类集比较）会
--      重新派生同步动作，删之无损，且顺带清掉链接分区残行。
--   3. op=DELETE 行中无法回填 base_id 的（文档行已物理不存在）一并删除：
--      其待删向量所属分区已不可知，由 UoW-5 存量全量重建兜底清理。
--   4. base_id 改 NOT NULL，唯一约束换到 (base_id, source_id, op)。
-- =============================================================================

ALTER TABLE agent_ai_knowledge_retrieval_sync_issue
    ADD COLUMN base_id BIGINT;

UPDATE agent_ai_knowledge_retrieval_sync_issue issue
SET base_id = document.base_id
FROM agent_ai_knowledge_document document
WHERE issue.base_id IS NULL
  AND document.base_id IS NOT NULL
  AND issue.source_id = 'kb:' || document.id;

DELETE FROM agent_ai_knowledge_retrieval_sync_issue
WHERE op = 'UPSERT';

DELETE FROM agent_ai_knowledge_retrieval_sync_issue
WHERE base_id IS NULL;

ALTER TABLE agent_ai_knowledge_retrieval_sync_issue
    ALTER COLUMN base_id SET NOT NULL;

ALTER TABLE agent_ai_knowledge_retrieval_sync_issue
    DROP CONSTRAINT uk_kb_retrieval_sync_issue;

ALTER TABLE agent_ai_knowledge_retrieval_sync_issue
    ADD CONSTRAINT uk_kb_retrieval_sync_issue UNIQUE (base_id, source_id, op);

COMMENT ON TABLE agent_ai_knowledge_retrieval_sync_issue IS '检索派生索引同步问题（待重试）：DELETE=空间分区待删残留（tombstone）';
COMMENT ON COLUMN agent_ai_knowledge_retrieval_sync_issue.base_id IS '目标空间 id（向量行所在的 base 分区）；唯一键 (base_id, source_id, op)';
COMMENT ON COLUMN agent_ai_knowledge_retrieval_sync_issue.agent_id IS '留痕：落行时文档所属空间的创建者 agent id（V58 起仅作追溯，不再是键）';
