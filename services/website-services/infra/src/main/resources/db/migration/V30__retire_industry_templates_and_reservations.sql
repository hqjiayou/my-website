-- website-services | Flyway V30 | 通用化全清：行业模板与预约功能下架（kou 2026-10-27 拍板）
-- 产品只做通用 agent：6 个行业种子模板软删；模板菜单与模板/预约权限软删并撤回角色授权。
-- 不 DROP 任何表、不删既有站点与预约数据；功能下架可逆（把 deleted 改回即可恢复种子与权限）。
-- 幂等：全部 UPDATE 带 deleted = FALSE 守卫。

-- ---------------------------------------------------------------------------
-- §1 软删行业种子模板（V7 种子：修车/二手车/蛋糕/美容/诊所/行政书士）
-- ---------------------------------------------------------------------------
UPDATE site_template
SET deleted = TRUE,
    deleted_at = now()
WHERE code IN (
        'japanese-car-repair-v1',
        'used-car-shop-v1',
        'cake-shop-v1',
        'salon-v1',
        'clinic-v1',
        'gyoseishoshi-v1'
    )
  AND deleted = FALSE;

-- ---------------------------------------------------------------------------
-- §2 撤回模板/预约权限与模板菜单的角色授权
-- ---------------------------------------------------------------------------
UPDATE admin_role_permission
SET deleted = TRUE
WHERE deleted = FALSE
  AND admin_permission_id IN (
        SELECT id
        FROM admin_permission
        WHERE permission_code = 'admin:menu:apps.templates'
           OR permission_code LIKE 'admin:template:%'
           OR permission_code LIKE 'admin:reservation:%'
    );

-- ---------------------------------------------------------------------------
-- §3 软删模板菜单与模板/预约权限本体
-- ---------------------------------------------------------------------------
UPDATE admin_permission
SET deleted = TRUE
WHERE deleted = FALSE
  AND (
        permission_code = 'admin:menu:apps.templates'
        OR permission_code LIKE 'admin:template:%'
        OR permission_code LIKE 'admin:reservation:%'
    );
