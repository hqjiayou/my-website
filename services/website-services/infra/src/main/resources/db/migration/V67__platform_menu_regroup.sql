-- =============================================================================
-- website-services | Flyway V67 | 平台区菜单重组（Issue #214 UoW-1）
-- 菜单按资源注册表四组重排：AGENT（admin:menu:apps，存量组复用）／模型／工具／统计。
-- 本步只动菜单行（种子与运行时同表）：
--   §1 新增模型／工具／统计三个分组父节点（无 route_path 的结构容器）；
--   §2 新分组绑 admin 角色（沿 V66 模式）；
--   §3 模型入口改名「模型 API 管理」并归模型组（route 不变，页面改造在 UoW-2）；
--   §4 新增 MCP 菜单归工具组（route 先指向 /ai/mcp-servers，页面在 UoW-5 建）；
--   §5 AI 用量归统计组；
--   §6 链预设／配置预设菜单下架：先撤角色绑定（仿 V30 §2），再软删本体；
--      两页与端点本步不动（页面撤除在 UoW-3／UoW-4）；
--   §7 兜底：仍无 icon 的在册 MENU 行统一补 'menu'，保证全部菜单有图标
--      （icon 值均为前端 utils/menu.ts 的 ICON_MAP 登记键）。
-- 不新增 ACTION 权限码；进页权限沿用既有口径。存量归组只动上述相关行，
-- API 管理（admin:menu:ai.proxyservices）本步不迁移（其页面并入在 UoW-2）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 分组父节点：模型（sort 30）／工具（40）／统计（50），排在系统管理（10）
-- 与 AGENT 组（20）之后；已存在的同名分组复用（NOT EXISTS 守卫）。
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT v.code, v.name, 'MENU', v.rkey, 'ACTIVE',
       NULL, v.sort, NULL::VARCHAR, v.icon, FALSE, 0
FROM (VALUES
    ('admin:menu:model', '模型', 'model', 30, 'cpu'),
    ('admin:menu:tools', '工具', 'tools', 40, 'blocks'),
    ('admin:menu:stats', '统计', 'stats', 50, 'chart')
) AS v(code, name, rkey, sort, icon)
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = v.code AND p.deleted = FALSE
);

-- ---------------------------------------------------------------------------
-- §2 新分组绑 admin 角色
-- ---------------------------------------------------------------------------
INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code IN ('admin:menu:model', 'admin:menu:tools', 'admin:menu:stats')
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );

-- ---------------------------------------------------------------------------
-- §3 模型入口 → 「模型 API 管理」，归模型组，补 icon
-- ---------------------------------------------------------------------------
UPDATE admin_permission
SET permission_name = '模型 API 管理',
    parent_id = (
        SELECT id FROM admin_permission
        WHERE permission_code = 'admin:menu:model' AND deleted = FALSE
    ),
    sort_order = 10,
    icon = 'plug',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:ai.modelentries'
  AND deleted = FALSE;

-- ---------------------------------------------------------------------------
-- §4 MCP 菜单（工具组，菜单先行；页面 UoW-5 建）
-- ---------------------------------------------------------------------------
INSERT INTO admin_permission (
    permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon, deleted, version)
SELECT 'admin:menu:ai.mcpservers', 'MCP', 'MENU', 'ai.mcpservers', 'ACTIVE',
       (SELECT id FROM admin_permission
        WHERE permission_code = 'admin:menu:tools' AND deleted = FALSE),
       10, '/ai/mcp-servers', 'server', FALSE, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission p
    WHERE p.permission_code = 'admin:menu:ai.mcpservers' AND p.deleted = FALSE
);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, version)
SELECT r.id, p.id, FALSE, 0
FROM admin_role r
CROSS JOIN admin_permission p
WHERE r.role_code = 'admin'
  AND r.deleted = FALSE
  AND p.deleted = FALSE
  AND p.status = 'ACTIVE'
  AND p.permission_code = 'admin:menu:ai.mcpservers'
  AND NOT EXISTS (
      SELECT 1 FROM admin_role_permission rp
      WHERE rp.admin_role_id = r.id
        AND rp.admin_permission_id = p.id
        AND rp.deleted = FALSE
  );

-- ---------------------------------------------------------------------------
-- §5 AI 用量归统计组（icon 沿用 V54 的 'chart'，此处一并钉死）
-- ---------------------------------------------------------------------------
UPDATE admin_permission
SET parent_id = (
        SELECT id FROM admin_permission
        WHERE permission_code = 'admin:menu:stats' AND deleted = FALSE
    ),
    sort_order = 10,
    icon = 'chart',
    updated_at = clock_timestamp()
WHERE permission_code = 'admin:menu:ai.usage'
  AND deleted = FALSE;

-- ---------------------------------------------------------------------------
-- §6 链预设／配置预设菜单下架：先撤角色绑定，再软删本体（仿 V30 §2／§3）
-- ---------------------------------------------------------------------------
UPDATE admin_role_permission
SET deleted = TRUE
WHERE deleted = FALSE
  AND admin_permission_id IN (
        SELECT id
        FROM admin_permission
        WHERE permission_code IN ('admin:menu:ai.chainpresets', 'admin:menu:ai.configpresets')
    );

UPDATE admin_permission
SET deleted = TRUE,
    updated_at = clock_timestamp()
WHERE deleted = FALSE
  AND permission_code IN ('admin:menu:ai.chainpresets', 'admin:menu:ai.configpresets');

-- ---------------------------------------------------------------------------
-- §7 兜底补 icon：任何仍无 icon 的在册 MENU 行（含运行时经菜单管理新建的行）
-- 统一补 'menu'（ICON_MAP 既有键），保证全部菜单有图标、不再渲染空圆圈。
-- ---------------------------------------------------------------------------
UPDATE admin_permission
SET icon = 'menu',
    updated_at = clock_timestamp()
WHERE resource_type = 'MENU'
  AND deleted = FALSE
  AND (icon IS NULL OR btrim(icon) = '');
