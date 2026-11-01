-- =============================================================================
-- website-services | Flyway V46 | AI 用量页菜单（Issue #140 UoW-2）
-- 仿 V38：挂到已有 /ai/settings 的父菜单下；没有该菜单时作为顶级项。
-- 进页与接口沿用 admin:ai-model:read，不新增 ACTION。
-- =============================================================================

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT 'admin:menu:ai.usage', 'AI 用量', 'MENU', 'ai.usage', 'ACTIVE',
       (SELECT parent_id FROM admin_permission
        WHERE permission_code <> 'admin:menu:ai.usage'
          AND route_path = '/ai/settings'
          AND resource_type = 'MENU'
          AND deleted = FALSE
        LIMIT 1),
       60, '/ai/usage', NULL, FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = 'admin:menu:ai.usage' AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code = 'admin:menu:ai.usage'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );
