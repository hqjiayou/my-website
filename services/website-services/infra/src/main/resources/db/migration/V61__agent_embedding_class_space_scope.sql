-- =============================================================================
-- website-services | Flyway V61 | 嵌入类登记作用域 agent→知识空间（Issue #209 UoW-3）
-- 登记表 agent_embedding_class 的 scope 由站点改为知识空间（base），与向量
-- 分区键（UoW-2 已改 base）对齐：一份文档的类集只由其所属空间的登记决定，
-- 不再经空间创建者的站点登记间接推导。
--   1. 新增 base_id 列；存量站点行（agent_id 非空、未软删）按「该 agent 创建的
--      每个空间一行」扇出复制（entry_id/enabled/sort_order 与审计列照旧），
--      agent_id 为 NULL 的全站行保持 base_id 为 NULL（平台默认 scope 不变）。
--      扇出后删除原站点行——没有空间的 agent 其登记行随之消失：该 agent 名下
--      没有任何知识空间，登记本就无消费方（口径在此写明，非数据丢失事故）。
--   2. 删除 agent_id 列；scope 索引改 (base_id, sort_order)。
--   3. 物化标记键转换：曾有 'AGENT:<creatorId>' 标记的创建者，其名下每个空间
--      补一行 'SPACE:<baseId>' 标记（防推算复活的保护随 scope 一并迁移），
--      再删除全部 'AGENT:%' 标记行。这是迁移期的一次性键转换，不是运行时删
--      标记——「marker 只增不删」的运行时纪律不变，转换后 SPACE/GLOBAL 两类
--      标记即唯一形态。
-- 已执行 Flyway 不改，本版全部为新增变更。版本号说明：V59/V60 已被其他解耦
-- 子单（#212 媒体平台化、#211 配置预设）占用，本单顺延 V61。
-- =============================================================================

-- 1. 登记行扇出到空间
ALTER TABLE agent_embedding_class
    ADD COLUMN base_id BIGINT;

-- 旧 scope 索引先删：唯一索引按 (COALESCE(agent_id, 0), entry_id) 判重，扇出
-- 插入的行尚未写回 scope 列（agent_id/base_id 皆空），同一 entry 扇出到多个
-- 空间会先互相撞唯一键；新索引在删列后按 base_id 口径重建。
-- 修复（2026-11-06，CI PG 合约实锤）：V48 建表时只建了 idx_agent_embedding_class_scope，
-- 从未建过 uk_agent_embedding_class_scope_entry；新鲜链上 DROP 会直接报错致整条
-- V61 失败。本版尚未在任何库成功执行过（首次真实应用即合约测试），就地改 IF EXISTS
-- 是唯一正确修法，不另立修补迁移。
DROP INDEX IF EXISTS uk_agent_embedding_class_scope_entry;
DROP INDEX IF EXISTS idx_agent_embedding_class_scope;

INSERT INTO agent_embedding_class (
    base_id, entry_id, enabled, sort_order,
    created_at, created_by, updated_at, updated_by, deleted, deleted_at, version)
SELECT b.id, c.entry_id, c.enabled, c.sort_order,
       c.created_at, c.created_by, c.updated_at, c.updated_by, false, NULL, 0
FROM agent_embedding_class c
JOIN agent_ai_knowledge_base b ON b.created_by_agent_id = c.agent_id
WHERE c.agent_id IS NOT NULL
  AND c.deleted = false;

DELETE FROM agent_embedding_class
WHERE agent_id IS NOT NULL;

-- 2. scope 列切换
ALTER TABLE agent_embedding_class
    DROP COLUMN agent_id;

CREATE UNIQUE INDEX uk_agent_embedding_class_scope_entry
    ON agent_embedding_class (COALESCE(base_id, 0), entry_id) WHERE deleted = false;
CREATE INDEX idx_agent_embedding_class_scope ON agent_embedding_class (base_id, sort_order);

COMMENT ON TABLE agent_embedding_class IS '嵌入类登记（#126，scope 于 #209 UoW-3 改知识空间）：一行一个登记类，只引用模型入口，类凭据运行时从入口解析';
COMMENT ON COLUMN agent_embedding_class.base_id IS '所属知识空间 id（agent_ai_knowledge_base）；NULL＝全站 scope（平台默认）';
COMMENT ON COLUMN agent_embedding_class.entry_id IS '被引用的模型入口 id（agent_model_entry）；须 mode=KEY 且 embeddingSpec 非空';

-- 3. 物化标记：AGENT:<creatorId> → 其名下每个空间 SPACE:<baseId>，再清旧键
INSERT INTO agent_embedding_class_materialization (scope_key, materialized_at)
SELECT 'SPACE:' || b.id, m.materialized_at
FROM agent_embedding_class_materialization m
JOIN agent_ai_knowledge_base b
  ON b.created_by_agent_id = CAST(substring(m.scope_key FROM 7) AS BIGINT)
WHERE m.scope_key LIKE 'AGENT:%'
ON CONFLICT (scope_key) DO NOTHING;

DELETE FROM agent_embedding_class_materialization
WHERE scope_key LIKE 'AGENT:%';

COMMENT ON TABLE agent_embedding_class_materialization IS '嵌入类登记物化标记（#126）：一行＝一个 scope 已完成首次读物化，只增不删防推算复活';
COMMENT ON COLUMN agent_embedding_class_materialization.scope_key IS '物化作用域键：GLOBAL＝全站，SPACE:<baseId>＝知识空间（#209 UoW-3 起；原 AGENT:<agentId> 已由 V61 一次性转换）';
