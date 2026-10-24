-- =============================================================================
-- website-services | Flyway V22 | site_reservation.idempotency_key
-- 前置：V14 site_reservation
-- internal 建单可选 Idempotency-Key；无键的历史行保持 NULL
-- =============================================================================

ALTER TABLE site_reservation ADD COLUMN idempotency_key VARCHAR(128);

COMMENT ON COLUMN site_reservation.idempotency_key IS
    '预约创建幂等键（如 rsv-<sha256>）；NULL = 未提供；同站点未删除行唯一';

CREATE UNIQUE INDEX uq_site_reservation_idempotency
    ON site_reservation (site_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = FALSE;
