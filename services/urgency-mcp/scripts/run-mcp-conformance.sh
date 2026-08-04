#!/usr/bin/env sh
set -eu

if [ "${MCP_CONFORMANCE_ENABLED:-}" != "true" ]; then
  echo "Skipping MCP conformance. Set MCP_CONFORMANCE_ENABLED=true to run it."
  exit 0
fi

URL="${MCP_CONFORMANCE_URL:-http://localhost:9090/urgency}"
SCENARIOS="${MCP_CONFORMANCE_SCENARIOS:-server-initialize ping tools-list}"
JAVA_BIN="${JAVA_BIN:-java}"
JAR="${MCP_SERVER_JAR:-target/urgency-mcp.jar}"
PROTOCOL_VERSION="${MCP_CONFORMANCE_PROTOCOL_VERSION:-2025-06-18}"

if [ ! -f "$JAR" ]; then
  echo "MCP server jar not found: $JAR" >&2
  exit 1
fi

"$JAVA_BIN" -Durgency.provider=local -jar "$JAR" > target/urgency-mcp-conformance.log 2>&1 &
SERVER_PID=$!

cleanup() {
  kill "$SERVER_PID" >/dev/null 2>&1 || true
  wait "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

legacy_readiness() {
  curl -fsS -X POST "$URL" \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json, text/event-stream' \
      -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"$PROTOCOL_VERSION\",\"capabilities\":{},\"clientInfo\":{\"name\":\"conformance-readiness\",\"version\":\"1.0.0\"}}}" \
      >/dev/null 2>&1
}

stateless_readiness() {
  curl -fsS -X POST "$URL" \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json' \
      -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
      -H 'Mcp-Method: server/discover' \
      -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/clientInfo\":{\"name\":\"conformance-readiness\",\"version\":\"1.0.0\"}}}}" \
      >/dev/null 2>&1
}

ready() {
  if [ "$PROTOCOL_VERSION" = "2026-07-28" ]; then
    stateless_readiness
  else
    legacy_readiness
  fi
}

i=0
while [ "$i" -lt 30 ]; do
  if ready; then
    break
  fi
  i=$((i + 1))
  sleep 1
done

if [ "$i" -eq 30 ]; then
  echo "MCP server did not become ready at $URL" >&2
  cat target/urgency-mcp-conformance.log >&2 || true
  exit 1
fi

for scenario in $SCENARIOS; do
  npx --yes @modelcontextprotocol/conformance server --url "$URL" --scenario "$scenario"
done
