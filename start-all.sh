#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$OPENAI_API_KEY" ]; then
  echo "ERROR: OPENAI_API_KEY is not set"
  exit 1
fi

echo "=== Starting Docker containers ==="
(cd "$ROOT/services/mediflow-helpdesk" && docker-compose up -d)
(cd "$ROOT/services/mediflow-similar-tickets" && docker-compose up -d)
(cd "$ROOT/services/mediflow-company-rag" && docker-compose up -d)

echo "=== Waiting for databases to be ready ==="
sleep 10

echo "=== Starting services ==="

(cd "$ROOT/services/mediflow-user-facing" && mvn quarkus:dev) &
sleep 5

(cd "$ROOT/services/mediflow-helpdesk" && mvn quarkus:dev -DDemoData=true) &
sleep 10

(cd "$ROOT/services/mediflow-ai-triage" && mvn quarkus:dev) &
sleep 5

(cd "$ROOT/services/mediflow-company-rag" && mvn quarkus:dev -DDemoData=true) &
sleep 5

(cd "$ROOT/services/mediflow-similar-tickets" && mvn clean verify && java -jar target/similar-tickets.jar) &

echo ""
echo "=== All services starting ==="
echo ""
echo "  mediflow-user-facing   http://localhost:8083"
echo "  mediflow-helpdesk      http://localhost:8080"
echo "  mediflow-ai-triage     http://localhost:8081"
echo "  mediflow-similar-tickets http://localhost:8082"
echo "  mediflow-company-rag   http://localhost:8084"
echo ""
echo "Press Ctrl+C to stop all services"

wait
