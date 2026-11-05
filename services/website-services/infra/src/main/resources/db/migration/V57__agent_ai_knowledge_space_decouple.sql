-- =============================================================================
-- website-services | Flyway V57 | 知识库空间化 Phase 1：数据模型（Issue #209 UoW-1）
-- base 去所有者化、link 表演进为订阅表、文档 agent_id 退役第一步：
--   1. base.agent_id 改名为 created_by_agent_id——创建者留痕，不再是所有者；
--      知识空间升为平台资源，agent 经订阅使用（管理面/可见性口径不变）。
--   2. agent_ai_knowledge_base_link 改名为 agent_ai_knowledge_base_subscription，
--      并为每个存量 base 回填一行创建者订阅（唯一约束 uk_kb_base_link 沿用不改名）。
--   3. document.agent_id 只 DROP NOT NULL＋注释标退役：代码侧已停写，所有者一律
--      经所属 base 的 created_by_agent_id 推导；删列留后续版本迁移。
-- 已执行 Flyway 不改，本版全部为新增变更。
-- =============================================================================

-- 1. base 去所有者化：agent_id → created_by_agent_id（创建者留痕，不再是所有者）
ALTER TABLE agent_ai_knowledge_base
    RENAME COLUMN agent_id TO created_by_agent_id;

COMMENT ON TABLE agent_ai_knowledge_base IS '知识空间（容器）：文档归属其一；平台资源，agent 经订阅使用，无所有者站点';
COMMENT ON COLUMN agent_ai_knowledge_base.created_by_agent_id IS '创建该空间的 agent id：创建者留痕，不再是所有者（可见性仍按创建者＋订阅者口径，见订阅表）';

-- 2. link 表改名订阅表，并回填存量创建者订阅行（已有同键行跳过）
ALTER TABLE agent_ai_knowledge_base_link
    RENAME TO agent_ai_knowledge_base_subscription;

INSERT INTO agent_ai_knowledge_base_subscription (base_id, agent_id)
SELECT b.id, b.created_by_agent_id
FROM agent_ai_knowledge_base b
WHERE NOT EXISTS (
    SELECT 1
    FROM agent_ai_knowledge_base_subscription s
    WHERE s.base_id = b.id
      AND s.agent_id = b.created_by_agent_id
);

COMMENT ON TABLE agent_ai_knowledge_base_subscription IS '知识空间订阅（纯关系行，删除即真删）：agent 订阅 base 后，其分区同步并可检索该空间文档；创建者行由 V57 回填';
COMMENT ON COLUMN agent_ai_knowledge_base_subscription.agent_id IS '订阅者 agent id';

-- 3. 文档 agent_id 退役第一步：停写（代码侧）后列改可空，删列留后续版本
ALTER TABLE agent_ai_knowledge_document
    ALTER COLUMN agent_id DROP NOT NULL;

COMMENT ON COLUMN agent_ai_knowledge_document.agent_id IS '退役中：已停写，文档所有者一律经所属 base 的 created_by_agent_id 推导；存量值仅作过渡期回退，待后续版本删列';
