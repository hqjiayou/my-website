-- 本地开发：插入 super_admin 账号 hqjiayou（密码明文 123456，仅本地使用）。
-- 推荐执行（在 website-services 目录下）：
--   pwsh ./scripts/local/run-seed-admin-hqjiayou-super.ps1
--   ./scripts/local/run-seed-admin-hqjiayou-super.sh
-- 前置：my_website_dev 已存在，且 Flyway V2 已写入 super_admin 角色。

WITH upsert_user AS (
    INSERT INTO admin_user (username, password_hash, display_name, status, deleted, version)
    SELECT 'hqjiayou',
           '$2y$10$KuaX56IJp68cMzPubuGQkeLiQLTxG4K8S5TdqpkOCZ6YSIAh5zDgi',
           'HQ Jiayou',
           'ACTIVE',
           FALSE,
           0
    WHERE NOT EXISTS (
        SELECT 1 FROM admin_user WHERE username = 'hqjiayou' AND deleted = FALSE
    )
    RETURNING id
),
existing_user AS (
    SELECT id FROM admin_user WHERE username = 'hqjiayou' AND deleted = FALSE
),
target_user AS (
    SELECT id FROM upsert_user
    UNION ALL
    SELECT id FROM existing_user
    LIMIT 1
),
super_role AS (
    SELECT id FROM admin_role WHERE role_code = 'super_admin' AND deleted = FALSE LIMIT 1
)
INSERT INTO admin_user_role (admin_user_id, admin_role_id, deleted, version)
SELECT tu.id, sr.id, FALSE, 0
FROM target_user tu
CROSS JOIN super_role sr
WHERE NOT EXISTS (
    SELECT 1
    FROM admin_user_role ur
    WHERE ur.admin_user_id = tu.id
      AND ur.admin_role_id = sr.id
      AND ur.deleted = FALSE
);
