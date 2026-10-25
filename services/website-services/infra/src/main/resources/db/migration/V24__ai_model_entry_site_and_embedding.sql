-- =============================================================================
-- website-services | Flyway V24 | 模型入口按站点，并记录向量模型 spec
-- site_id 为空：全站默认。非空：该站点的默认入口优先于全站。
-- embedding_spec 为空：检索仍用进程 AI_EMBEDDING_MODEL。
-- =============================================================================

ALTER TABLE ai_model_entry
    ADD COLUMN site_id BIGINT,
    ADD COLUMN embedding_spec VARCHAR(256);

ALTER TABLE ai_model_entry
    ADD CONSTRAINT fk_ai_model_entry_site
        FOREIGN KEY (site_id) REFERENCES site (id) ON DELETE RESTRICT;

COMMENT ON COLUMN ai_model_entry.site_id IS '空=全站入口；非空=只服务该站点';
COMMENT ON COLUMN ai_model_entry.embedding_spec IS '可选。如 google:gemini-embedding-001；与本入口同一把 key';

DROP INDEX uq_ai_model_entry_one_default;

CREATE UNIQUE INDEX uq_ai_model_entry_platform_default
    ON ai_model_entry ((1))
    WHERE deleted = FALSE AND is_default = TRUE AND site_id IS NULL;

CREATE UNIQUE INDEX uq_ai_model_entry_site_default
    ON ai_model_entry (site_id)
    WHERE deleted = FALSE AND is_default = TRUE AND site_id IS NOT NULL;
