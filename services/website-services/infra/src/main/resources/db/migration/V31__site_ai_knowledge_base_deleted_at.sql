-- V29 建 site_ai_knowledge_base 时漏了平台基类 AbstractAuditableEntity 对应的
-- deleted_at 列（其余审计表都有），实体查询在真 PG 上报 column does not exist。
-- H2 测试 schema 按实体抄写、带该列，掩盖了缺漏；此处补齐并与实体对齐。
-- IF NOT EXISTS：已手工补列的环境（如本地开发库）可平滑通过。
ALTER TABLE site_ai_knowledge_base
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN site_ai_knowledge_base.deleted_at IS '软删时间（平台审计基类约定列，V29 漏建，V31 补齐）';
