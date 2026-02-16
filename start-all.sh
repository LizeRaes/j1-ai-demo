#!/usr/bin/env bash

ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ -z "$OPENAI_API_KEY" ]; then
  echo "ERROR: OPENAI_API_KEY is not set"
  exit 1
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
  docker rm -f ticketing-mysql oracle-free 2>/dev/null || true
  (cd "$ROOT/services/medicalappointment-helpdesk" && docker compose down --remove-orphans) 2>/dev/null || true
  (cd "$ROOT/services/medicalappointment-similar-tickets" && docker compose down --remove-orphans) 2>/dev/null || true
  (cd "$ROOT/services/medicalappointment-company-rag" && docker compose down --remove-orphans) 2>/dev/null || true
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

# --- Start infrastructure ---
echo "=== Starting Docker containers ==="
(cd "$ROOT/services/medicalappointment-helpdesk" && docker compose up -d) || { echo "FATAL: helpdesk DB failed"; exit 1; }
(cd "$ROOT/services/medicalappointment-similar-tickets" && docker compose up -d) || { echo "FATAL: similar-tickets DB failed"; exit 1; }
(cd "$ROOT/services/medicalappointment-company-rag" && docker compose up -d) || { echo "FATAL: company-rag DB failed"; exit 1; }

echo "=== Waiting for databases to be ready ==="
sleep 10

# --- Start application services ---
echo "=== Starting services ==="

(cd "$ROOT/services/medicalappointment-user-facing" && exec mvn quarkus:dev) &
PIDS+=($!)
sleep 5

(cd "$ROOT/services/medicalappointment-helpdesk" && exec mvn quarkus:dev -DDemoData=true) &
PIDS+=($!)
sleep 10

(cd "$ROOT/services/medicalappointment-ai-triage" && exec mvn quarkus:dev) &
PIDS+=($!)
sleep 5

(cd "$ROOT/services/medicalappointment-company-rag" && exec mvn quarkus:dev -DDemoData=true) &
PIDS+=($!)
sleep 5

(cd "$ROOT/services/medicalappointment-similar-tickets" && mvn clean verify && exec java -jar target/similar-tickets.jar) &
PIDS+=($!)

echo ""
echo "=== All services starting ==="
echo ""
echo "  medicalappointment-user-facing   http://localhost:8083"
echo "  medicalappointment-helpdesk      http://localhost:8080"
echo "  medicalappointment-ai-triage     http://localhost:8081"
echo "  medicalappointment-similar-tickets http://localhost:8082"
echo "  medicalappointment-company-rag   http://localhost:8084"
echo ""
echo "Press Ctrl+C to stop all services"

wait
