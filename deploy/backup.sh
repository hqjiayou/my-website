#!/usr/bin/env bash
# 通过 compose exec 调用容器内 pg_dump。口令留在 postgres 容器环境里，本脚本不写凭据。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BACKUP_DIR="${ROOT}/backups"
mkdir -p "$BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
final="${BACKUP_DIR}/my-website-${stamp}.sql"
tmp="${BACKUP_DIR}/.my-website-${stamp}.sql.partial"

cleanup() {
  rm -f "$tmp"
}
trap cleanup EXIT

docker compose exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl' \
  > "$tmp"

mv "$tmp" "$final"
trap - EXIT

mapfile -t files < <(
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'my-website-*.sql' -printf '%T@\t%p\n' \
    | sort -nr \
    | cut -f2-
)
if ((${#files[@]} > 14)); then
  rm -f "${files[@]:14}"
fi
