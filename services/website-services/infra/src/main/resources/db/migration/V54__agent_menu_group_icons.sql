-- =============================================================================
-- website-services | Flyway V52 | agent 侧栏三项收束 + 图标
-- AI 用量（V46）曾挂 /ai/settings 的父级；库中无该菜单时 parent 落成 NULL，
-- 与智能体 / API 管理拆开。此处挂回 admin:menu:apps，并补齐三项 icon。
-- =============================================================================

UPDATE admin_permission
SET parent_id = (
        SELECT id FROM admin_permission
        WHERE permission_code = 'admin:menu:apps' AND deleted = FALSE
    ),
    sort_order = 30,
    icon = 'chart',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:ai.usage'
  AND deleted = FALSE;

UPDATE admin_permission
SET icon = 'zap',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:ai.proxyservices'
  AND deleted = FALSE;

UPDATE admin_permission
SET icon = 'layers',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:apps.sites'
  AND deleted = FALSE
  AND (icon IS NULL OR btrim(icon) = '');
