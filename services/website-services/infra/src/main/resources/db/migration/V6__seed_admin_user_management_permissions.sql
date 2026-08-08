-- =============================================================================
-- website-services | Flyway V6 | Admin User CRUD：写操作 ACTION 权限
-- 前置：V2 已有 admin:user:list / admin:user:detail / admin:user:role:*
-- 幂等：permission_code / role_permission 绑定均 NOT EXISTS 守卫（对齐 V5）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 User 管理 ACTION（create / update / delete / status）
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'ACTION', v.rkey, 'ACTIVE',
       NULL, 0, NULL, NULL, FALSE, 0
FROM (VALUES
    ('admin:user:create', '创建用户', 'user:create'),
    ('admin:user:update', '更新用户', 'user:update'),
    ('admin:user:delete', '删除用户', 'user:delete'),
    ('admin:user:status', '变更用户状态', 'user:status')
) AS v(code, name, rkey)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- §2 admin 角色补绑新增 ACTIVE 权限（沿用 V2/V4/V5 admin:% 策略）
-- ---------------------------------------------------------------------------
INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code LIKE 'admin:%'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );
