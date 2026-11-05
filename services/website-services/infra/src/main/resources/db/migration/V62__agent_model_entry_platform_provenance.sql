-- =============================================================================
-- website-services | Flyway V62 | 模型入口平台化（Issue #210 UoW-6，解耦 Phase 2）
-- agent_model_entry.agent_id 作用域退役：存量专属入口转平台注册，原归属转存
-- source_agent_id 作来源标注（留痕；组链按「带来源标注优先层 → 平台原生层」
-- 组装，单 agent 形态下与改造前逐条等价，口径同 lessons 的 sourceAgentId 范本）。
-- agent_id 本版只停写＋置空，删列留后续版本（与 Phase 1 文档列同口径）。
-- 默认唯一性由「作用域 × 模式」改为「来源 × 模式」：COALESCE(source_agent_id, 0)
-- 中 0 代表平台原生来源，与改造前的 (agent_id, mode) 分组一一对应，存量不冲突。
-- =============================================================================

ALTER TABLE agent_model_entry
    ADD COLUMN source_agent_id BIGINT;

COMMENT ON COLUMN agent_model_entry.source_agent_id IS '来源标注：入口平台化前所属的 agent id；平台原生入口为空（Issue #210 UoW-6）';
COMMENT ON COLUMN agent_model_entry.agent_id IS '已退役（Issue #210 UoW-6）：入口平台化后停写恒空，删列留后续版本';

UPDATE agent_model_entry
   SET source_agent_id = agent_id
 WHERE agent_id IS NOT NULL;

DROP INDEX IF EXISTS uq_agent_model_entry_platform_default;
DROP INDEX IF EXISTS uq_agent_model_entry_scoped_default;

UPDATE agent_model_entry
   SET agent_id = NULL
 WHERE agent_id IS NOT NULL;

CREATE UNIQUE INDEX uq_agent_model_entry_default_per_origin
    ON agent_model_entry (mode, COALESCE(source_agent_id, 0))
    WHERE deleted = FALSE AND is_default = TRUE;
