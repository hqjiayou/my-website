-- 以具备 CREATEDB 权限的超级用户连接到 maintenance database「postgres」后执行。
-- 若角色 hqjiayou 尚不存在，请先执行：
--   CREATE ROLE hqjiayou LOGIN PASSWORD 'hqjiayou';
-- （口令须与本机 DB_PASSWORD / PostgreSQL 实际口令一致。）
--
-- psql 示例：
--   psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -f scripts/create-my_website_dev.sql

CREATE DATABASE my_website_dev
    OWNER hqjiayou
    ENCODING 'UTF8';
