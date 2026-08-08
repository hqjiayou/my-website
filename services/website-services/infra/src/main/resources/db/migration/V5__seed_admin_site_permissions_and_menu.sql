-- =============================================================================
-- website-services | Flyway V5 | Site Core：站点 ACTION + 业务应用 MENU
-- 前置：V2 已有 admin:site:list / admin:site:detail；V4 已有 system MENU 树
-- 幂等：permission_code / role_permission 绑定均 NOT EXISTS 守卫
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 Site ACTION（list/detail 已在 V2，此处仅补写操作与状态变更）
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'ACTION', v.rkey, 'ACTIVE',
       NULL, 0, NULL, NULL, FALSE, 0
FROM (VALUES
    ('admin:site:create', '创建站点', 'site:create'),
    ('admin:site:update', '更新站点', 'site:update'),
    ('admin:site:delete', '删除站点', 'site:delete'),
    ('admin:site:status', '变更站点状态', 'site:status')
) AS v(code, name, rkey)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- §2 MENU 树：业务应用 → 站点管理
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'MENU', v.rkey, 'ACTIVE',
       NULL, v.sort, v.path, v.icon, FALSE, 0
FROM (VALUES
    ('admin:menu:apps', '业务应用', 'apps', 20, NULL::VARCHAR, 'appstore')
) AS v(code, name, rkey, sort, path, icon)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'MENU', v.rkey, 'ACTIVE',
       (SELECT id FROM admin_permission p
        WHERE p.permission_code = 'admin:menu:apps' AND p.deleted = FALSE),
       v.sort, v.path, v.icon, FALSE, 0
FROM (VALUES
    ('admin:menu:apps.sites', '站点管理', 'apps.sites', 10, '/apps/sites', 'layers')
) AS v(code, name, rkey, sort, path, icon)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- §3 admin 角色补绑新增 ACTIVE 权限（沿用 V2/V4 admin:% 策略）
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
