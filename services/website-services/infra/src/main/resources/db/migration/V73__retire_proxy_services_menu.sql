-- =============================================================================
-- website-services | Flyway V73 | 「API 管理」菜单退役（Issue #215）
-- 旧页 /agent/api 已撤除：其 KEY 页签（供应商 API key 直连条目管理）整块
-- 搬入「模型 API 管理」页作独立分节，CLI 页签本体早于 Issue #214 UoW-2
-- 已被模型页的代理来源详情复用；/agent/api 与 /ai/proxy-services 两条旧
-- 路由改重定向到 /ai/model-entries。本脚本只退役菜单行：先撤 admin 角色
-- 绑定、再软删本体（仿 V67 §6 链／配置预设菜单下架写法，deleted = FALSE
-- 守卫保证口径一致）。
-- =============================================================================

UPDATE admin_role_permission
SET deleted = TRUE
WHERE deleted = FALSE
  AND admin_permission_id IN (
        SELECT id
        FROM admin_permission
        WHERE permission_code = 'admin:menu:ai.proxyservices'
    );

UPDATE admin_permission
SET deleted = TRUE,
    updated_at = clock_timestamp()
WHERE deleted = FALSE
  AND permission_code = 'admin:menu:ai.proxyservices';
