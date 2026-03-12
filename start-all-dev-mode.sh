#!/usr/bin/env bash

ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$OPENAI_API_KEY" ]; then
  echo "WARN: OPENAI_API_KEY is not set; similar-tickets will use services/similar-tickets/config/config-prod.yaml if present"
fi

SHOW_EVENT_LOG="${SHOW_EVENT_LOG:-false}"
UI_ZOOM_PERCENT="${UI_ZOOM_PERCENT:-100}"
for arg in "$@"; do
  case "$arg" in
    -show-event-log|--show-event-log)
      SHOW_EVENT_LOG=true
      ;;
    -ui-zoom=*|--ui-zoom=*)
      UI_ZOOM_PERCENT="${arg#*=}"
      ;;
    -h|--help)
      echo "Usage: ./start-all-dev-mode.sh [-show-event-log] [-ui-zoom=<percent>]"
      echo "  -show-event-log   Show left-side event/activity log panels in UIs (default: hidden)"
      echo "  -ui-zoom=<n>      Set default UI zoom percent for all service dashboards (default: 100)"
      exit 0
      ;;
    *)
      echo "ERROR: Unknown option '$arg'"
      echo "Usage: ./start-all-dev-mode.sh [-show-event-log] [-ui-zoom=<percent>]"
      exit 1
      ;;
  esac
done

if ! [[ "$UI_ZOOM_PERCENT" =~ ^[0-9]+$ ]]; then
  echo "ERROR: -ui-zoom must be a positive integer (got '$UI_ZOOM_PERCENT')"
  exit 1
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

  sleep 1
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "ERROR: $name exited immediately after startup"
    return 1
  fi

  wait_for_http "$name" "$url"
}

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

echo "=== Cleaning up old containers ==="
stop_containers

echo "=== Starting Docker containers ==="
docker compose up -d
echo "=== Waiting for dependencies to be ready ==="
wait_for_port "mysql" 3306 120
wait_for_port "ticket oracle" 1521 120
wait_for_port "company oracle" 1522 120
wait_for_oracle_sql "ticket oracle" "ticket" 240
wait_for_oracle_sql "company oracle" "company" 240
wait_for_http "docling" "http://localhost:5001/openapi.json" 180

echo "=== Starting services in dev mode ==="
echo "  demo flags: helpdesk=$HELPDESK_DEMO_DATA company-rag=$COMPANY_RAG_DEMO_DATA similar-tickets=$SIMILAR_TICKETS_DEMO_DATA"
echo "  ui flags: show-event-log=$SHOW_EVENT_LOG"
echo "  ui zoom: $UI_ZOOM_PERCENT%"
echo "  note: similar-tickets is Helidon and runs from jar (no Quarkus hot-reload)"

# Quarkus services with hot-reload
start_service "medicapt-user-facing" "services/medicapt-user-facing" "http://localhost:8083" mvn quarkus:dev
start_service "helpdesk" "services/helpdesk" "http://localhost:8080" env HELPDESK_UI_SHOW_EVENT_LOG="$SHOW_EVENT_LOG" HELPDESK_UI_DEFAULT_ZOOM_PERCENT="$UI_ZOOM_PERCENT" mvn quarkus:dev $HELPDESK_DEMO_FLAG
start_service "urgency" "services/urgency" "http://localhost:8086" env URGENCY_EMBEDDING_PROVIDER=openai mvn -Durgency.embedding-provider=openai quarkus:dev
start_service "ai-triage" "services/ai-triage" "http://localhost:8081" env AI_TRIAGE_UI_SHOW_EVENT_LOG="$SHOW_EVENT_LOG" AI_TRIAGE_UI_DEFAULT_ZOOM_PERCENT="$UI_ZOOM_PERCENT" mvn quarkus:dev
start_service "company-rag" "services/company-rag" "http://localhost:8084" env UI_SHOW_EVENT_LOG="$SHOW_EVENT_LOG" UI_FONT_ZOOM_DEFAULT="$UI_ZOOM_PERCENT" mvn quarkus:dev $COMPANY_RAG_DEMO_FLAG
start_service "coding-assistant" "services/coding-assistant" "http://localhost:8085" env CODING_ASSISTANT_UI_DEFAULT_ZOOM_PERCENT="$UI_ZOOM_PERCENT" mvn quarkus:dev

build_service "similar-tickets" "services/similar-tickets"
start_service "similar-tickets" "services/similar-tickets" "http://localhost:8082" java -Dconfig.profile=prod -Dui.show.event-log="$SHOW_EVENT_LOG" -Dui.font.zoom.default="$UI_ZOOM_PERCENT" $SIMILAR_TICKETS_DEMO_FLAG -jar target/similarity.jar

echo ""
echo "=== All services started (dev mode) ==="
echo ""
echo "  medicapt-user-facing             http://localhost:8083   (quarkus:dev)"
echo "  helpdesk                         http://localhost:8080   (quarkus:dev)"
echo "  ai-triage                        http://localhost:8081   (quarkus:dev)"
echo "  urgency                          http://localhost:8086   (quarkus:dev)"
echo "  similar-tickets                  http://localhost:8082   (jar, no hot-reload)"
echo "  company-rag                      http://localhost:8084   (quarkus:dev)"
echo "  coding-assistant                 http://localhost:8085   (quarkus:dev)"
echo ""
echo "Press Ctrl+C to stop all services and containers"

wait
