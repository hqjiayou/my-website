#!/usr/bin/env bash
set -euo pipefail
if [[ -f /tmp/agent-services-contract.pid ]]; then
  kill "$(cat /tmp/agent-services-contract.pid)" 2>/dev/null || true
  rm -f /tmp/agent-services-contract.pid
fi
