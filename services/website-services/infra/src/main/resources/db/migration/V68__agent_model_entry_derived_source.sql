-- =============================================================================
-- website-services | Flyway V68 | 模型入口派生标记（Issue #214 UoW-2）
-- CLI 条目改为由代理账号＋模型派生物化：派生行以 derived_source 辨识
-- （NULL＝人工登记；'PROXY'＝由 CLI 代理当前账号/模型清单同步生成）。
-- 派生行在管理面只读：改/删由服务层拦截（admin.ai.model-entry.derived-read-only）。
-- 代理侧已消失的模型，其派生行只停用不硬删，避免链预设成员 id 悬空。
-- 本迁移只加列，不改任何既有数据与旧迁移。
-- =============================================================================

ALTER TABLE agent_model_entry
    ADD COLUMN derived_source VARCHAR(32);

COMMENT ON COLUMN agent_model_entry.derived_source IS '派生来源标记：NULL=人工登记，PROXY=CLI 代理派生（Issue #214 UoW-2）';
