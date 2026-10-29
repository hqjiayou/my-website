-- 侧栏不再叫「站点管理」。路径仍是 /apps/sites，只改展示名。
UPDATE admin_permission
SET permission_name = '智能体',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:apps.sites'
  AND deleted = FALSE
  AND permission_name = '站点管理';
