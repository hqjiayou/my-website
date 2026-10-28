#!/usr/bin/env bash
# ./scripts/local/run-seed-site-demo.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICES_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_FILE="${SCRIPT_DIR}/seed-site-demo.sql"
ENV_FILE="${SERVICES_ROOT}/.env"
CONTAINER="${CONTAINER:-local-dev-postgres-1}"

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "SQL 文件不存在: ${SQL_FILE}" >&2
  exit 1
fi

read_dotenv() {
  local key="$1"
  local default="$2"
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "${default}"
    return
  fi
  local line
  line="$(grep -E "^[[:space:]]*${key}=" "${ENV_FILE}" | tail -n 1 || true)"
  if [[ -z "${line}" ]]; then
    echo "${default}"
    return
  fi
  echo "${line#*=}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"
}

DB_NAME="$(read_dotenv POSTGRES_DB my_website_dev)"
DB_USER="$(read_dotenv POSTGRES_USER hqjiayou)"
DB_PASSWORD="$(read_dotenv POSTGRES_PASSWORD hqjiayou)"

echo "==> seed site: siteCode=demo username=site_demo database=${DB_NAME} user=${DB_USER}"

if [[ "${USE_HOST_PSQL:-}" == "1" ]]; then
  export PGPASSWORD="${DB_PASSWORD}"
  psql -h localhost -p 5432 -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
  unset PGPASSWORD
else
  docker exec -i "${CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 < "${SQL_FILE}"
fi

echo "==> 完成。可使用 siteCode=demo / site_demo / 123456 登录前台（仅本地）。"
