#!/usr/bin/env sh
set -eu

if [ "${MCP_CONFORMANCE_ENABLED:-}" != "true" ]; then
  echo "Skipping MCP conformance. Set MCP_CONFORMANCE_ENABLED=true to run it."
  exit 0
fi

URL="${MCP_CONFORMANCE_URL:-http://localhost:9090/urgency}"
JAVA_BIN="${JAVA_BIN:-java}"
JAR="${MCP_SERVER_JAR:-target/urgency-mcp.jar}"
PROTOCOL_VERSION="${MCP_CONFORMANCE_PROTOCOL_VERSION:-2026-07-28}"
DRAFT_CHECKS="${MCP_DRAFT_CHECKS:-server-discover tools-list reject-session-header}"

if [ "$PROTOCOL_VERSION" = "2026-07-28" ]; then
  OFFICIAL_SCENARIOS="${MCP_CONFORMANCE_SCENARIOS:- ping tools-list}"
else
  OFFICIAL_SCENARIOS="${MCP_CONFORMANCE_SCENARIOS:-server-initialize ping tools-list}"
fi

if [ ! -f "$JAR" ]; then
  echo "MCP server jar not found: $JAR" >&2
  exit 1
fi

"$JAVA_BIN" --enable-preview -Durgency.provider=local -jar "$JAR" > target/urgency-mcp-conformance.log 2>&1 &
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


stateless_request() {
  method="$1"
  name="${2:-}"
  body="$3"
  if [ -n "$name" ]; then
    curl -fsS -X POST "$URL" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
        -H "Mcp-Method: $method" \
        -H "Mcp-Name: $name" \
        -d "$body" \
        >/dev/null
  else
    curl -fsS -X POST "$URL" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
        -H "Mcp-Method: $method" \
        -d "$body" \
        >/dev/null
  fi
}

stateless_rejects_session_header() {
  status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$URL" \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json' \
      -H "MCP-Protocol-Version: $PROTOCOL_VERSION" \
      -H 'Mcp-Method: ping' \
      -H 'Mcp-Session-Id: legacy-session' \
      -d '{"jsonrpc":"2.0","id":1,"method":"ping"}')
  test "$status" = "400"
}

run_draft_check() {
  case "$1" in
    server-discover)
      stateless_request server/discover "" '{"jsonrpc":"2.0","id":1,"method":"server/discover"}'
      ;;
    tools-list)
      stateless_request tools/list "" '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
      ;;
    reject-session-header)
      stateless_rejects_session_header
      ;;
    *)
      echo "Unknown draft check '$1'" >&2
      exit 1
      ;;
  esac
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

if [ "$PROTOCOL_VERSION" = "2026-07-28" ]; then
  for check in $DRAFT_CHECKS; do
    echo "Running MCP draft check: $check"
    run_draft_check "$check"
  done
  if [ -z "$OFFICIAL_SCENARIOS" ]; then
    echo "Skipping official MCP conformance scenarios for draft protocol $PROTOCOL_VERSION."
    echo "Set MCP_CONFORMANCE_SCENARIOS to run scenarios available in the published runner."
  fi
fi

for scenario in $OFFICIAL_SCENARIOS; do
  npx --yes @modelcontextprotocol/conformance server --url "$URL" --scenario "$scenario"
done
