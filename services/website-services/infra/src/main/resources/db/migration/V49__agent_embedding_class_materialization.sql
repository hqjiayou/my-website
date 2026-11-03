-- =============================================================================
-- website-services | Flyway V49 | 嵌入类登记首次读物化标记（Issue #126 UoW-2）
-- 来源切换：embeddingClasses 改读登记表 agent_embedding_class。存量 scope
-- （站点/全站）第一次被读到时，按旧推算口径把当时的类物化成登记行，本表
-- 一行 marker 证明该 scope 已物化过：scope_key = 'GLOBAL'（全站）或
-- 'AGENT:<agentId>'（站点）。
-- marker 只增不删：物化后登记行是唯一真相源，人工把某 scope 的登记行删光
-- 也不会被推算结果「复活」，因为读取只在 marker 缺失时才物化。
-- 本表无审计列，物化时间即 materialized_at；写入与登记行插入在同一事务。
-- =============================================================================

CREATE TABLE agent_embedding_class_materialization (
    scope_key       VARCHAR(64) PRIMARY KEY,
    materialized_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE agent_embedding_class_materialization IS '嵌入类登记物化标记（#126）：一行＝一个 scope 已完成首次读物化，只增不删防推算复活';
COMMENT ON COLUMN agent_embedding_class_materialization.scope_key IS '物化作用域键：GLOBAL＝全站，AGENT:<agentId>＝站点';
COMMENT ON COLUMN agent_embedding_class_materialization.materialized_at IS '该 scope 完成物化的时间';
