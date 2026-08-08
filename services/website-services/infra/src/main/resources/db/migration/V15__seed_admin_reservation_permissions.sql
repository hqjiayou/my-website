-- =============================================================================
-- website-services | Flyway V15 | Reservation ACTION + admin 角色补绑
-- 前置：V14 site_reservation；V2 admin 角色；幂等 NOT EXISTS
-- update：覆盖 status PATCH（本轮无全文 PUT）
-- =============================================================================

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'ACTION', v.rkey, 'ACTIVE',
       NULL, 0, NULL, NULL, FALSE, 0
FROM (VALUES
    ('admin:reservation:list', '列出站点预约', 'reservation:list'),
    ('admin:reservation:create', '创建站点预约', 'reservation:create'),
    ('admin:reservation:update', '变更站点预约状态', 'reservation:update'),
    ('admin:reservation:delete', '删除站点预约', 'reservation:delete')
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
  AND p.permission_code LIKE 'admin:reservation:%'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );
