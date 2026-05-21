-- 5C：Admin RBAC 基础种子（仅数据，不改表结构）
-- 幂等：已存在且未删除的 role_code / permission_code 不重复插入

-- ---------------------------------------------------------------------------
-- admin_permission
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (permission_code, permission_name, resource_type, resource_key, status, deleted, version)
SELECT v.code, v.name, v.rtype, v.rkey, 'ACTIVE', FALSE, 0
FROM (VALUES
    ('admin:role:list',              '角色列表',           'ACTION', 'role:list'),
    ('admin:role:detail',            '角色详情',           'ACTION', 'role:detail'),
    ('admin:role:create',            '创建角色',           'ACTION', 'role:create'),
    ('admin:role:update',            '更新角色',           'ACTION', 'role:update'),
    ('admin:role:delete',            '删除角色',           'ACTION', 'role:delete'),
    ('admin:role:permission:view',   '查看角色权限',       'ACTION', 'role:permission:view'),
    ('admin:role:permission:update', '更新角色权限',       'ACTION', 'role:permission:update'),
    ('admin:permission:list',        '权限列表',           'ACTION', 'permission:list'),
    ('admin:user:list',              '用户列表',           'ACTION', 'user:list'),
    ('admin:user:detail',            '用户详情',           'ACTION', 'user:detail'),
    ('admin:user:role:view',         '查看用户角色',       'ACTION', 'user:role:view'),
    ('admin:user:role:update',       '更新用户角色',       'ACTION', 'user:role:update'),
    ('admin:menu:view',              '菜单查看',           'MENU',   'menu:view'),
    ('admin:site:list',              '站点列表',           'ACTION', 'site:list'),
    ('admin:site:detail',            '站点详情',           'ACTION', 'site:detail'),
    ('admin:dashboard:view',         '仪表盘',             'ACTION', 'dashboard:view')
) AS v(code, name, rtype, rkey)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- admin_role
-- ---------------------------------------------------------------------------
INSERT INTO admin_role (role_code, role_name, description, status, deleted, version)
SELECT v.code, v.name, v.descr, 'ACTIVE', FALSE, 0
FROM (VALUES
    ('super_admin', '超级管理员', '拥有全部权限（Checker 短路）'),
    ('admin',       '系统管理员', '后台管理常规角色')
) AS v(code, name, descr)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_role r
    WHERE r.role_code = v.code AND r.deleted = FALSE
);

-- admin 角色：绑定除 super_admin 专属外的管理权限（super_admin 不依赖绑定）
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
