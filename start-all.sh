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
  HELPDESK_DEMO_FLAG="-Ddemo.data.enabled=true"
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

build_service() {
  local name=$1 dir=$2
  echo "=== Building $name ==="
  (cd "$ROOT/$dir" && mvn -DskipTests clean package)
}

start_service() {
  local name=$1 dir=$2 url=$3
  shift 3

  echo "=== Starting $name ==="
  (
    cd "$ROOT/$dir" || exit 1
    exec "$@"
  ) &
  local pid=$!
  PIDS+=($pid)

  # Fail fast if the service process exits right away
  sleep 1
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "ERROR: $name exited immediately after startup"
    return 1
  fi

  wait_for_http "$name" "$url"
}

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
  docker compose -f "$ROOT/services/company-rag/docker-compose.yml" down --remove-orphans
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

wait_for_oracle_sql() {
  local name=$1 container=$2 timeout=${3:-240}
  local elapsed=0 output=""
  if ! docker container inspect "$container" >/dev/null 2>&1; then
    echo "ERROR: Container '$container' not found while waiting for $name SQL readiness"
    return 1
  fi
  while true; do
    output=$(docker exec "$container" bash -lc "sqlplus -s vector/vector@localhost:1521/freepdb1 <<'SQL'
set heading off
set feedback off
set pagesize 0
set verify off
set echo off
select 'READY' from dual;
exit;
SQL" 2>/dev/null || true)
    if [[ "$output" == *"READY"* ]]; then
      echo "  [ready] $name (SQL READY)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    if [ $((elapsed % 20)) -eq 0 ]; then
      echo "  [wait] $name SQL not ready yet (${elapsed}s)"
    fi
    if [ "$elapsed" -ge "$timeout" ]; then
      echo "ERROR: Timed out waiting for $name SQL readiness"
      return 1
    fi
  done
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
echo "=== Waiting for dependencies to be ready ==="
wait_for_port "mysql" 3306 120
wait_for_port "ticket oracle" 1521 120
wait_for_port "company oracle" 1522 120
wait_for_oracle_sql "ticket oracle" "ticket" 240
wait_for_oracle_sql "company oracle" "company" 240
wait_for_http "docling" "http://localhost:5001/openapi.json" 180

# --- Start application services ---
echo "=== Building services ==="
echo "  demo flags: helpdesk=$HELPDESK_DEMO_DATA company-rag=$COMPANY_RAG_DEMO_DATA similar-tickets=$SIMILAR_TICKETS_DEMO_DATA"

build_service "medicapt-user-facing" "services/medicapt-user-facing"
build_service "helpdesk" "services/helpdesk"
build_service "urgency" "services/urgency"
# Optional MCP demo variant:
# build_service "urgency-mcp-helidon" "services/urgency-mcp-helidon"
build_service "ai-triage" "services/ai-triage"
build_service "company-rag" "services/company-rag"
build_service "similar-tickets" "services/similar-tickets"
build_service "coding-assistant" "services/coding-assistant"

echo "=== Starting services (deterministic order) ==="

start_service "medicapt-user-facing" "services/medicapt-user-facing" "http://localhost:8083" java -jar target/quarkus-app/quarkus-run.jar
start_service "helpdesk" "services/helpdesk" "http://localhost:8080" java -jar $HELPDESK_DEMO_FLAG target/quarkus-app/quarkus-run.jar
start_service "urgency" "services/urgency" "http://localhost:8086" java -jar target/quarkus-app/quarkus-run.jar
# Optional MCP demo variant:
# start_service "urgency-mcp-helidon" "services/urgency-mcp-helidon" "http://localhost:9090/urgency" java -jar target/urgency-mcp-helidon.jar
start_service "ai-triage" "services/ai-triage" "http://localhost:8081" java -jar target/quarkus-app/quarkus-run.jar
start_service "company-rag" "services/company-rag" "http://localhost:8084" java -jar $COMPANY_RAG_DEMO_FLAG target/quarkus-app/quarkus-run.jar
start_service "similar-tickets" "services/similar-tickets" "http://localhost:8082" java -Dconfig.profile=prod $SIMILAR_TICKETS_DEMO_FLAG -jar target/similarity.jar
start_service "coding-assistant" "services/coding-assistant" "http://localhost:8085" java -jar target/quarkus-app/quarkus-run.jar

echo ""
echo "=== All services starting ==="
echo ""
echo "  medicapt-user-facing             http://localhost:8083"
echo "  helpdesk                         http://localhost:8080"
echo "  ai-triage                        http://localhost:8081"
echo "  urgency                          http://localhost:8086"
echo "  similar-tickets                  http://localhost:8082"
echo "  company-rag                      http://localhost:8084"
echo "  coding-assistant                 http://localhost:8085"
echo ""
echo "=== All services started ==="
echo ""
echo "Press Ctrl+C to stop all services"

wait
