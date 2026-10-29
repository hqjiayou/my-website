-- 权限管理页不再作为后台入口。菜单仍是权限行，停用后侧栏不再展示。
UPDATE admin_permission
SET status = 'DISABLED',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:system.permission'
  AND deleted = FALSE
  AND status = 'ACTIVE';
