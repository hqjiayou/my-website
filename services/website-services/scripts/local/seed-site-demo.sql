-- 本地开发：插入 ACTIVE 站点 demo + site_user site_demo（密码明文 123456，仅本地使用）。
-- 推荐执行（在 website-services 目录下）：
--   pwsh ./scripts/local/run-seed-site-demo.ps1
--   ./scripts/local/run-seed-site-demo.sh
-- 前置：my_website_dev 已存在，且 Flyway V1 已创建 site / site_user。

WITH upsert_site AS (
    INSERT INTO site (site_code, site_name, site_type, status, deleted, version)
    SELECT 'demo',
           'Demo Site',
           'CAR',
           'ACTIVE',
           FALSE,
           0
    WHERE NOT EXISTS (
        SELECT 1 FROM site WHERE site_code = 'demo' AND deleted = FALSE
    )
    RETURNING id
),
existing_site AS (
    SELECT id FROM site WHERE site_code = 'demo' AND deleted = FALSE
),
target_site AS (
    SELECT id FROM upsert_site
    UNION ALL
    SELECT id FROM existing_site
    LIMIT 1
)
INSERT INTO site_user (site_id, username, password_hash, status, deleted, version)
SELECT ts.id,
       'site_demo',
       -- BCrypt for plaintext 123456（与本地 admin seed 同源哈希）
       '$2y$10$KuaX56IJp68cMzPubuGQkeLiQLTxG4K8S5TdqpkOCZ6YSIAh5zDgi',
       'ACTIVE',
       FALSE,
       0
FROM target_site ts
WHERE NOT EXISTS (
    SELECT 1
    FROM site_user su
    WHERE su.site_id = ts.id
      AND su.username = 'site_demo'
      AND su.deleted = FALSE
);
