-- 列表地址改为 /agent。API 管理挂到 agent 下面，不再留在 AI 菜单。
UPDATE admin_permission
SET route_path = '/agent',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:apps.sites'
  AND deleted = FALSE
  AND route_path = '/apps/sites';

UPDATE admin_permission
SET parent_id = (
        SELECT id FROM admin_permission
        WHERE permission_code = 'admin:menu:apps' AND deleted = FALSE
    ),
    route_path = '/agent/api',
    sort_order = 20,
    permission_name = 'API 管理',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:ai.proxyservices'
  AND deleted = FALSE;
