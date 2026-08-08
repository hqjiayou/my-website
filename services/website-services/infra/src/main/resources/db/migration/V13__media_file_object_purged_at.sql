-- =============================================================================
-- website-services | Flyway V13 | media_file.object_purged_at
-- orphan 扫尾：对象 remove 成功后打标，避免软删行反复占满 batch、更晚候选永远清不到
-- =============================================================================

ALTER TABLE media_file
    ADD COLUMN object_purged_at TIMESTAMPTZ;

COMMENT ON COLUMN media_file.object_purged_at IS
    '对象存储已清理时间；非空则不再进入 orphan 候选（行可因 cover_media_id FK 保留）';

CREATE INDEX idx_media_file_orphan_pending
    ON media_file (id)
    WHERE object_purged_at IS NULL
      AND (deleted = TRUE OR status IN ('FAILED', 'UPLOADING'));
