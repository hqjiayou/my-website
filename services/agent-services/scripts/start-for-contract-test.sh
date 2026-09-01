#!/usr/bin/env bash
# 后台启动 agent-services（契约集成测试 / CI 用）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export AGENT_INTERNAL_SERVICE_TOKEN="${AGENT_INTERNAL_SERVICE_TOKEN:-contract-test-token}"
export AGENT_CONTRACT_USE_TEST_MODEL="${AGENT_CONTRACT_USE_TEST_MODEL:-true}"
export PORT="${PORT:-3001}"

uv sync --frozen --extra dev

nohup uv run uvicorn agent_services.app:create_app \
  --factory \
  --host 127.0.0.1 \
  --port "$PORT" \
  > /tmp/agent-services-contract.log 2>&1 &
echo $! > /tmp/agent-services-contract.pid

for i in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:${PORT}/health" >/dev/null; then
    echo "agent-services ready on :${PORT}"
    exit 0
  fi
  sleep 1
done

echo "agent-services failed to become ready; log:" >&2
cat /tmp/agent-services-contract.log >&2 || true
exit 1
