-- =============================================================================
-- website-services | Flyway V51 | 用量记账来源新增 EXPERIENCE（Issue #184 UoW-2）
-- P3 定案：经验提炼的模型调用按 #140 一表制记账、source 标 EXPERIENCE，
-- 成本在用量页可见。agent_ai_usage.source 的 CHECK（V45）只认三值，
-- 本脚本放宽纳入 EXPERIENCE，并同步列注释；其余约束与数据不动。
-- =============================================================================

ALTER TABLE agent_ai_usage DROP CONSTRAINT ck_agent_ai_usage_source;
ALTER TABLE agent_ai_usage
    ADD CONSTRAINT ck_agent_ai_usage_source
        CHECK (source IN ('PORTAL', 'ADMIN_PREVIEW', 'GATEWAY_V1', 'EXPERIENCE'));

COMMENT ON COLUMN agent_ai_usage.source IS '来源：PORTAL／ADMIN_PREVIEW／GATEWAY_V1／EXPERIENCE（经验提炼，#184）';
