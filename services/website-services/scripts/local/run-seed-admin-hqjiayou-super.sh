#!/usr/bin/env bash
# 在本地 PostgreSQL 中插入 super_admin 账号 hqjiayou（密码 123456，仅本地开发）。
# 用法：
#   ./scripts/local/run-seed-admin-hqjiayou-super.sh
#   USE_HOST_PSQL=1 ./scripts/local/run-seed-admin-hqjiayou-super.sh
#   POSTGRES_CONTAINER=my-postgres ./scripts/local/run-seed-admin-hqjiayou-super.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICES_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SQL_FILE="$SCRIPT_DIR/seed-admin-hqjiayou-super.sql"
ENV_FILE="$SERVICES_ROOT/.env"

CONTAINER="${POSTGRES_CONTAINER:-local-dev-postgres-1}"
USE_HOST_PSQL="${USE_HOST_PSQL:-0}"

read_dotenv() {
  local key="$1"
  local default="$2"
  if [[ -f "$ENV_FILE" ]]; then
    local line
    line="$(grep -E "^[[:space:]]*${key}=" "$ENV_FILE" | tail -n 1 || true)"
    if [[ -n "$line" ]]; then
      echo "${line#*=}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^["'\'']//' -e 's/["'\'']$//'
      return
    fi
  fi
  echo "$default"
}

DB_NAME="$(read_dotenv POSTGRES_DB my_website_dev)"
DB_USER="$(read_dotenv POSTGRES_USER hqjiayou)"
DB_PASSWORD="$(read_dotenv POSTGRES_PASSWORD hqjiayou)"

echo "==> seed admin: username=hqjiayou role=super_admin database=$DB_NAME user=$DB_USER"

if [[ "$USE_HOST_PSQL" == "1" ]]; then
  export PGPASSWORD="$DB_PASSWORD"
  psql -h localhost -p 5432 -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
  unset PGPASSWORD
else
  if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    echo "错误: 容器 '$CONTAINER' 未运行。请启动 local-stack 或设置 POSTGRES_CONTAINER / USE_HOST_PSQL=1" >&2
    exit 1
  fi
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f - <"$SQL_FILE"
fi

echo "==> 完成。可使用 hqjiayou / 123456 登录管理端（仅本地）。"
