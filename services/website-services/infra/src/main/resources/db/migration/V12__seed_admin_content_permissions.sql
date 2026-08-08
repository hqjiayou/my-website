-- =============================================================================
-- website-services | Flyway V12 | Content ACTION + admin 角色补绑
-- 前置：V11 site_content；V2 admin 角色；幂等 NOT EXISTS
-- =============================================================================

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'ACTION', v.rkey, 'ACTIVE',
       NULL, 0, NULL, NULL, FALSE, 0
FROM (VALUES
    ('admin:content:list', '列出站点内容', 'content:list'),
    ('admin:content:create', '创建站点内容', 'content:create'),
    ('admin:content:update', '更新站点内容', 'content:update'),
    ('admin:content:delete', '删除站点内容', 'content:delete'),
    ('admin:content:status', '变更站点内容状态', 'content:status')
) AS v(code, name, rkey)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code LIKE 'admin:content:%'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );
