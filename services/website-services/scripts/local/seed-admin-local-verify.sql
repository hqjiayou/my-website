-- 本地阶段 4C（Admin 认证联调）专用：插入一条 ACTIVE 管理员账号。
-- 密码明文（仅供本地）：Local4c_Verify
-- BCrypt（cost=10）：使用 Spring BCryptPasswordEncoder 生成；算法前缀 $2a/$2b 均可由 Spring 校验。
--
-- 执行示例（按你的 PG 容器调整连接参数）：
--   psql "postgresql://USER:PASS@localhost:5432/my_website_dev" -v ON_ERROR_STOP=1 -f scripts/local/seed-admin-local-verify.sql

INSERT INTO admin_user (username, password_hash, display_name, status)
SELECT 'admin_local_verify',
       '$2b$10$TRen32n2zx/DMiV0rbUYT./n.2GA043pXZUYIQtxtJ.bWHftpExSW',
       'Local 4C Verify',
       'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM admin_user WHERE username = 'admin_local_verify' AND deleted = FALSE
);
