-- 持久层 PostgreSQL 回归验证专用临时库（不影响开发库 my_website_dev）。
-- 以具备 CREATEDB 权限的超级用户连接到 maintenance database「postgres」后执行。
--
-- psql 示例（口令按本机实际情况替换）：
--   psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -f scripts/create-my_website_persistence_verify.sql
--
-- Docker（local-stack / local-postgres）：与 scripts/README「持久层验证库」一致；`-U`/OWNER 须与本机 website-services `.env` 中 Postgres 用户一致。
--   docker exec -i local-postgres psql -U mywebsite -d postgres -c "CREATE DATABASE my_website_3c_verify OWNER mywebsite ENCODING 'UTF8';"

CREATE DATABASE my_website_3c_verify
    OWNER hqjiayou
    ENCODING 'UTF8';
