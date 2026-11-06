-- =============================================================================
-- website-services | Flyway V72 | 删除退役引用列（Issue #214 UoW-7 收尾）
-- 删除 agent 表的两列历史引用及其 FK：
--   chain_preset_id  （V63 建，FK fk_agent_chain_preset → agent_ai_chain_preset）
--   config_preset_id （V60 建，FK fk_agent_config_preset → agent_ai_config_preset）
-- 删除依据（等价前提）：生效值早已拷入 agent 自有字段——V69 已把各 agent 现行
-- 引用预设的成员按模式拆分拷入 chain_key_entry_ids / chain_cli_entry_ids，
-- V70 已把引用预设的 prompt 拷为本地副本（prompt_system_prompt /
-- prompt_temperature / prompt_template_id）、skills 拷入 default_skill_ids；
-- 两列自 #214 UoW-3 / UoW-4 起运行时停读停写（组链只读自有链字段、Prompt
-- 解析只读本地副本），代码侧实体字段与残留引用已在本步一并删除。本迁移只
-- 收尾删列，不动数据语义：迁移前后同一 agent 的解析结果不变。
-- 两张预设表本身保留：agent_ai_chain_preset 是现役链模板库（套用即拷贝），
-- agent_ai_config_preset 保留备查（管理面已于 UoW-4 退役）。两列均无索引。
-- =============================================================================

ALTER TABLE agent DROP CONSTRAINT fk_agent_chain_preset;
ALTER TABLE agent DROP CONSTRAINT fk_agent_config_preset;
ALTER TABLE agent DROP COLUMN chain_preset_id;
ALTER TABLE agent DROP COLUMN config_preset_id;
