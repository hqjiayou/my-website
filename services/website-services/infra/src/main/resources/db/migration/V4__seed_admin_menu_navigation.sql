-- =============================================================================
-- website-services | Flyway V4 | 5E 菜单导航种子（MENU 树 + menu 域 ACTION）
-- 前置：V3 已扩展 parent_id / sort_order / route_path / icon
-- 幂等：permission_code / role_permission 绑定均 NOT EXISTS 守卫
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 停用 V2 占位 MENU（由 admin:menu:system 体系替代）
-- ---------------------------------------------------------------------------
UPDATE admin_permission
SET status = 'DISABLED',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:view'
  AND deleted = FALSE
  AND status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- §2 MENU 树（resource_type = MENU）
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'MENU', v.rkey, 'ACTIVE',
       NULL, v.sort, v.path, v.icon, FALSE, 0
FROM (VALUES
    ('admin:menu:system', '系统管理', 'system', 10, NULL::VARCHAR, 'setting')
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
        WHERE p.permission_code = 'admin:menu:system' AND p.deleted = FALSE),
       v.sort, v.path, v.icon, FALSE, 0
FROM (VALUES
    ('admin:menu:system.user',       '用户管理', 'system.user',       10, '/system/users',       'user'),
    ('admin:menu:system.role',       '角色管理', 'system.role',       20, '/system/roles',       'team'),
    ('admin:menu:system.permission', '权限管理', 'system.permission', 30, '/system/permissions', 'safety'),
    ('admin:menu:system.menu',       '菜单管理', 'system.menu',       40, '/system/menus',       'menu')
) AS v(code, name, rkey, sort, path, icon)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- §3 菜单管理接口鉴权 ACTION（与 MENU 可见性码分离）
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'ACTION', v.rkey, 'ACTIVE',
       NULL, 0, NULL, NULL, FALSE, 0
FROM (VALUES
    ('admin:menu:list',   '菜单树查询', 'menu:list'),
    ('admin:menu:detail', '菜单详情',   'menu:detail'),
    ('admin:menu:create', '新增菜单',   'menu:create'),
    ('admin:menu:update', '修改菜单',   'menu:update'),
    ('admin:menu:delete', '删除菜单',   'menu:delete'),
    ('admin:menu:sort',   '菜单排序',   'menu:sort'),
    ('admin:menu:bind',   '菜单绑定权限', 'menu:bind')
) AS v(code, name, rkey)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- §4 admin 角色补绑新增 ACTIVE 权限（沿用 V2 admin:% 策略）
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
