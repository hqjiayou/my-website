-- 持久层 PostgreSQL 回归验证专用临时库（不影响开发库 my_website_dev）。
-- 以具备 CREATEDB 权限的超级用户连接到 maintenance database「postgres」后执行。
--
-- psql 示例（口令按本机实际情况替换）：
--   psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 -f scripts/create-my_website_persistence_verify.sql
--
-- Docker Compose（仓库 postgres 服务已启动）：与 README「持久层验证库」小节一致；`-U`/OWNER 须与本机 POSTGRES_USER 一致（compose 默认为 hqjiayou）。
--   docker compose exec postgres psql -U hqjiayou -d postgres -c "CREATE DATABASE my_website_3c_verify OWNER hqjiayou ENCODING 'UTF8';"

CREATE DATABASE my_website_3c_verify
    OWNER hqjiayou
    ENCODING 'UTF8';
