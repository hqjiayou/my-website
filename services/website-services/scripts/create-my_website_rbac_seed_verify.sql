-- 5C.1：RBAC 种子 Flyway 真实 PostgreSQL 验证专用库（不影响开发库 my_website_dev / 持久层库 my_website_3c_verify）。
-- 以具备 CREATEDB 权限的用户连接 maintenance database「postgres」后执行。
--
-- psql 示例：
--   psql -h localhost -p 5432 -U hqjiayou -d postgres -v ON_ERROR_STOP=1 -f scripts/create-my_website_rbac_seed_verify.sql
--
-- Docker（local-postgres）：
--   docker exec -i local-postgres psql -U hqjiayou -d postgres -v ON_ERROR_STOP=1 -f - < scripts/create-my_website_rbac_seed_verify.sql

CREATE DATABASE my_website_rbac_seed_verify
    OWNER hqjiayou
    ENCODING 'UTF8';
