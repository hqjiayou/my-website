-- =============================================================================
-- website-services | Flyway V66 | 平台区新页菜单（Issue #213 UoW-C）
-- 仿 V46：模型入口／链预设自 /ai/settings 拆为独立页、配置预设为新页，
-- 三项挂到已有 /ai/settings 的父菜单下；没有该菜单时作为顶级项。
-- 进页权限沿用既有 ACTION：入口／链预设 admin:ai-model:read、
-- 配置预设 admin:ai-prompt:read，不新增 ACTION。
-- 存量 AI 菜单项的父级归组属运行时数据（菜单管理 UI），本迁移不重组既有行。
-- =============================================================================

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT 'admin:menu:ai.modelentries', '模型入口', 'MENU', 'ai.modelentries', 'ACTIVE',
       (SELECT parent_id FROM admin_permission
        WHERE permission_code <> 'admin:menu:ai.modelentries'
          AND route_path = '/ai/settings'
          AND resource_type = 'MENU'
          AND deleted = FALSE
        LIMIT 1),
       70, '/ai/model-entries', NULL, FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = 'admin:menu:ai.modelentries' AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code = 'admin:menu:ai.modelentries'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT 'admin:menu:ai.chainpresets', '链预设', 'MENU', 'ai.chainpresets', 'ACTIVE',
       (SELECT parent_id FROM admin_permission
        WHERE permission_code <> 'admin:menu:ai.chainpresets'
          AND route_path = '/ai/settings'
          AND resource_type = 'MENU'
          AND deleted = FALSE
        LIMIT 1),
       80, '/ai/chain-presets', NULL, FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = 'admin:menu:ai.chainpresets' AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code = 'admin:menu:ai.chainpresets'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT 'admin:menu:ai.configpresets', '配置预设', 'MENU', 'ai.configpresets', 'ACTIVE',
       (SELECT parent_id FROM admin_permission
        WHERE permission_code <> 'admin:menu:ai.configpresets'
          AND route_path = '/ai/settings'
          AND resource_type = 'MENU'
          AND deleted = FALSE
        LIMIT 1),
       90, '/ai/config-presets', NULL, FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = 'admin:menu:ai.configpresets' AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code = 'admin:menu:ai.configpresets'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );
