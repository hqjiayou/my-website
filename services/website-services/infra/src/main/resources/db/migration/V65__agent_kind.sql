-- =============================================================================
-- website-services | Flyway V65 | agent 类型维度 agent_kind（Issue #213 UoW-A）
-- 解耦收官后 agent 侧只剩「类型＋关联」：本列是 agent 的类型维度，取值
-- SITE＝站点型 / AI_ONLY＝纯 AI 型（封闭二值枚举，服务层校验）。
-- 与既有 agent_type 互不相关：agent_type 是创建时填的自由文本站点用途
-- 标签（PERSONAL / BLOG / 自订标签等），不承担类型语义，不要混用。
--
-- 存量回填：靠 DEFAULT 'SITE' 一次性全量回填。理由：存量 agent 全部经
-- 站点创建流程产生（建站向导＋站点工作台），按站点型处理与现状一致；
-- 个别实为纯 AI 型的，后续在新列表页逐个改
-- （PUT /api/admin/agents/{id}/kind）。
-- =============================================================================

ALTER TABLE agent
    ADD COLUMN agent_kind VARCHAR(16) NOT NULL DEFAULT 'SITE';

COMMENT ON COLUMN agent.agent_kind IS 'agent 类型（#213）：SITE＝站点型，AI_ONLY＝纯 AI 型；与 agent_type（自由文本用途标签）无关';
