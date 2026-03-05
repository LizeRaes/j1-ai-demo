#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

SERVICES=(
  "services/medicapt-user-facing"
  "services/helpdesk"
  "services/ai-triage"
  "services/similar-tickets"
  "services/company-rag"
  "services/coding-assistant"
)

echo "=== Running tests for all services ==="

for service in "${SERVICES[@]}"; do
  echo ""
  echo ">>> ${service}"
  (cd "$ROOT/$service" && mvn clean test)
done

echo ""
echo "=== All service tests passed ==="
