#!/usr/bin/env bash
# One-command local run of the admin-web browser E2E suite (Issue #2).
#
#   cd apps/admin-web && ./e2e/run-e2e.sh
#
# Brings up the disposable stack (e2e/docker-compose.e2e.yml), creates the
# media bucket, seeds the super-admin user with the repo's own local seed
# (hqjiayou / 123456, only inside this stack), then runs Playwright.
# Requires: docker (compose v2), pnpm, openssl.
# Teardown: docker compose -f e2e/docker-compose.e2e.yml down -v
set -euo pipefail

cd "$(dirname "$0")/.." # apps/admin-web
COMPOSE="docker compose -f e2e/docker-compose.e2e.yml"
SEED_SQL="../../services/website-services/scripts/local/seed-admin-hqjiayou-super.sql"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is required to run the E2E stack (compose v2)." >&2
  exit 1
fi

# 1. JWT keys for the auth service (throwaway, gitignored).
mkdir -p e2e/secrets
if [ ! -f e2e/secrets/auth-private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out e2e/secrets/auth-private.pem
  openssl rsa -pubout -in e2e/secrets/auth-private.pem -out e2e/secrets/auth-public.pem
fi

# 2. Stack up (admin runs Flyway; --wait blocks on healthchecks).
$COMPOSE up -d --build --wait postgres redis minio admin auth gateway admin-web

# 3. Media bucket (backend does not create it).
$COMPOSE run --rm minio-init

# 4. Seed the super-admin user (idempotent SQL from the repo).
$COMPOSE exec -T postgres psql -U mywebsite -d my_website_e2e <"$SEED_SQL"

# 5. Deps + browser + tests.
pnpm install --frozen-lockfile
pnpm exec playwright install --with-deps chromium || pnpm exec playwright install chromium
E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:8081}" pnpm test:e2e
