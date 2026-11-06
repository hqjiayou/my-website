-- =============================================================================
-- website-services | Flyway V71 | Skills 货架菜单（Issue #214 UoW-6）
-- 工具组新增「Skills 货架」只读页菜单（route /ai/skills，icon 用 UoW-1 已在
-- 前端 ICON_MAP 预登记的 sliders）。技能本体是 agent-services 的文件定义
-- （skills/{id}/SKILL.md），货架页只展示目录与 B 层自选开关状态，无数据表
-- 变更；写法仿 V70 §4（菜单插入＋admin 角色绑定，均带 NOT EXISTS 幂等守卫）。
-- 工具组内排序：MCP 10、Prompt 模板 20、Skills 货架 40。
-- =============================================================================

INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT 'admin:menu:ai.skills', 'Skills 货架', 'MENU', 'ai.skills', 'ACTIVE',
       (SELECT id FROM admin_permission
        WHERE permission_code = 'admin:menu:tools' AND deleted = FALSE),
       40, '/ai/skills', 'sliders', FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = 'admin:menu:ai.skills' AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code = 'admin:menu:ai.skills'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );
