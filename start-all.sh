#!/usr/bin/env bash

ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$OPENAI_API_KEY" ]; then
  echo "WARN: OPENAI_API_KEY is not set; similar-tickets will use services/similar-tickets/config/config-prod.yaml if present"
fi

# Demo-data toggles (override via env vars when needed)
HELPDESK_DEMO_DATA="${HELPDESK_DEMO_DATA:-true}"
COMPANY_RAG_DEMO_DATA="${COMPANY_RAG_DEMO_DATA:-true}"
SIMILAR_TICKETS_DEMO_DATA="${SIMILAR_TICKETS_DEMO_DATA:-true}"

HELPDESK_DEMO_FLAG=""
if [ "$HELPDESK_DEMO_DATA" = "true" ]; then
  HELPDESK_DEMO_FLAG="-DDemoData=true"
fi

COMPANY_RAG_DEMO_FLAG=""
if [ "$COMPANY_RAG_DEMO_DATA" = "true" ]; then
  COMPANY_RAG_DEMO_FLAG="-Ddemo.data.load=true"
fi

SIMILAR_TICKETS_DEMO_FLAG=""
if [ "$SIMILAR_TICKETS_DEMO_DATA" = "true" ]; then
  SIMILAR_TICKETS_DEMO_FLAG="-DDemoData=true"
fi

PIDS=()

# Recursively kill a process and all its descendants
kill_tree() {
  local pid=$1 sig=${2:-TERM}
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$child" "$sig"
  done
  kill -"$sig" "$pid" 2>/dev/null || true
}

stop_containers() {
  docker compose down --remove-orphans
}

wait_for_port() {
  local name=$1 port=$2 timeout=${3:-180}
  local elapsed=0
  while ! nc -z localhost "$port" >/dev/null 2>&1; do
    sleep 1
    elapsed=$((elapsed + 1))
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "ERROR: Timed out waiting for $name on port $port"
      return 1
    fi
  done
  echo "  [ready] $name (port $port)"
}

wait_for_http() {
  local name=$1 url=$2 timeout=${3:-240}
  local elapsed=0 code=""
  while true; do
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "$url" || true)
    if [ "$code" != "000" ]; then
      echo "  [ready] $name ($url, status $code)"
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "ERROR: Timed out waiting for $name at $url"
      return 1
    fi
  done
}

cleanup() {
  echo ""
  echo "=== Shutting down ==="

  for pid in "${PIDS[@]}"; do
    kill_tree "$pid"
  done
  sleep 2
  for pid in "${PIDS[@]}"; do
    kill_tree "$pid" 9
  done
  wait 2>/dev/null || true

  echo "=== Stopping Docker containers ==="
  stop_containers

  echo "=== All stopped ==="
}

trap cleanup EXIT
trap 'exit 0' INT TERM

# --- Clean up leftovers from previous runs ---
echo "=== Cleaning up old containers ==="
stop_containers

# --- Start databases ---
echo "=== Starting Docker containers ==="
  docker compose up -d
echo "=== Waiting for databases to be ready ==="
wait_for_port "mysql" 3306 120
wait_for_port "ticket oracle" 1521 120
wait_for_port "company oracle" 1522 120

# --- Start application services ---
echo "=== Starting services ==="
echo "  demo flags: helpdesk=$HELPDESK_DEMO_DATA company-rag=$COMPANY_RAG_DEMO_DATA similar-tickets=$SIMILAR_TICKETS_DEMO_DATA"

(cd "$ROOT/services/medicapt-user-facing" && exec mvn quarkus:dev) &
PIDS+=($!)

(cd "$ROOT/services/helpdesk" && exec mvn quarkus:dev $HELPDESK_DEMO_FLAG) &
PIDS+=($!)

(cd "$ROOT/services/ai-triage" && exec mvn quarkus:dev) &
PIDS+=($!)

(cd "$ROOT/services/company-rag" && exec mvn quarkus:dev $COMPANY_RAG_DEMO_FLAG) &
PIDS+=($!)

(cd "$ROOT/services/similar-tickets" && mvn -Dmaven.test.skip=true clean package && exec java -Dconfig.profile=prod $SIMILAR_TICKETS_DEMO_FLAG -jar target/similar-tickets.jar) &
PIDS+=($!)

(cd "$ROOT/services/coding-assistant" && exec mvn quarkus:dev) &
PIDS+=($!)

echo ""
echo "=== All services starting ==="
echo ""
echo "  medicapt-user-facing             http://localhost:8083"
echo "  helpdesk                         http://localhost:8080"
echo "  ai-triage                        http://localhost:8081"
echo "  similar-tickets                  http://localhost:8082"
echo "  company-rag                      http://localhost:8084"
echo "  coding-assistant                 http://localhost:8085"
echo ""
echo "=== Waiting for services to be reachable ==="
wait_for_http "medicapt-user-facing" "http://localhost:8083"
wait_for_http "helpdesk" "http://localhost:8080"
wait_for_http "ai-triage" "http://localhost:8081"
wait_for_http "similar-tickets" "http://localhost:8082"
wait_for_http "company-rag" "http://localhost:8084"
wait_for_http "coding-assistant" "http://localhost:8085"
echo "=== All services started ==="
echo ""
echo "Press Ctrl+C to stop all services"

wait
