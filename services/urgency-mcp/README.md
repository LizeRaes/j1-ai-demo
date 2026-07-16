# Urgency MCP Server

Helidon 4.4.0 HTTP server exposing urgency inference for support ticket text through a stateless MCP-compatible JSON-RPC endpoint. The MCP endpoint is `http://localhost:9090/urgency` and the tool returns a score from `0.0` to `10.0`.

The service supports two embedding providers that both feed a DeepNetts urgency scorer:

- `local`: deterministic local feature hashing, with the scorer model name, location, and embedding dimensions configured in `application.yaml`.
- `openai`: OpenAI embeddings, defaulting to `text-embedding-3-small`, followed by the configured DeepNetts scorer. The API key comes from config, a system property, or `OPENAI_API_KEY`.

## MCP 2026-07-28 Migration

Before this migration, `/urgency` was served by the Helidon MCP annotation server. Readiness used the older `initialize` handshake, conformance targeted an older protocol version, and the endpoint did not perform explicit stateless header validation.

After this migration, `/urgency` is a small stateless HTTP adapter. It does not require `initialize`, does not create or return `Mcp-Session-Id`, and requires these headers on every request:

- `MCP-Protocol-Version: 2026-07-28`
- `Mcp-Method: <json-rpc-method>`
- `Mcp-Name: getUrgency` for `tools/call`

The adapter supports `server/discover`, `ping`, `tools/list`, and `tools/call`. `tools/list` advertises the single `getUrgency` tool, a JSON Schema 2020-12 input schema requiring `phrase`, and cache metadata with `ttlMs: 300000` and `cacheScope: "public"`.

Before, readiness looked like this older initialize request:

```bash
curl -X POST http://localhost:9090/urgency \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"readiness","version":"1.0.0"}}}'
```

Now, use `server/discover` with the stateless protocol headers:

```bash
curl -X POST http://localhost:9090/urgency \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: server/discover' \
  -d '{"jsonrpc":"2.0","id":1,"method":"server/discover"}'
```

List tools:

```bash
curl -X POST http://localhost:9090/urgency \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/list' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Call the urgency tool:

```bash
curl -X POST http://localhost:9090/urgency \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/call' \
  -H 'Mcp-Name: getUrgency' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"getUrgency","arguments":{"phrase":"patient cannot access insulin refill"}}}'
```

## Inference Architecture

`UrgencyInferenceService` is intentionally thin. It validates the incoming complaint and delegates scoring to a provider-specific inference engine. Provider selection and startup configuration are modeled separately so those paths can be tested directly:

- `UrgencyProvider` parses `urgency.provider`.
- `UrgencyInferenceConfiguration` models either local or OpenAI configuration.
- `LocalInferenceSettings` resolves local scorer and embedding settings.
- `LocalInferenceResources` validates the local scorer file and lazily loads the DeepNetts scorer and embedding generator.
- `DeepNettsInferenceResources` validates and lazily loads the configured DeepNetts scorer.
- `DeepNettsUrgencyInferenceEngine` scores both local and OpenAI embeddings with that scorer.

This keeps config parsing, model path resolution, startup validation, lazy resource creation, and scoring behavior independently testable without exposing private methods from the service.

The MCP HTTP layer follows the same structure. `McpUrgencyServer` only adapts Helidon requests and responses. Protocol behavior lives in `com.example.urgency.mcp`, where `McpProtocolHandler`, `McpRequestValidator`, `McpDiscoveryDocument`, `McpToolCatalog`, and `McpToolCallService` can be tested without starting a web server.

## Configuration

Edit `src/main/resources/application.yaml` or override the same keys with `-D...` system properties.

```yaml
server:
  port: 9090

urgency:
  provider: local
  providers:
    local:
      model:
        name: model-scorer-local.dnet
        location: ../urgency/model
      embedding:
        name: feature-hash-1536
        location: ../urgency/model/local-embeddings
        dimensions: 1536
    openai:
      model:
        name: model-scorer-openai.dnet
        location: ../urgency/model
      embedding:
        model:
          name: text-embedding-3-small
        dimensions: 1536

openai:
  api-key: ""
```

Important keys:

- `urgency.provider`: `local` or `openai`.
- `urgency.providers.local.model.name`: local provider DeepNetts scorer file name. The sample config points to the checked-in scorer; replace it with a scorer trained for the local embedding model for production local inference.
- `urgency.providers.local.model.location`: directory containing the local scorer.
- `urgency.providers.local.embedding.name`: local embedding model identifier used as part of the deterministic local embedding seed.
- `urgency.providers.local.embedding.location`: local embedding model location used as part of the deterministic local embedding seed.
- `urgency.providers.local.embedding.dimensions`: local embedding vector width; keep it aligned with the selected DeepNetts scorer input size.
- `urgency.providers.openai.model.name`: OpenAI-compatible DeepNetts scorer file name.
- `urgency.providers.openai.model.location`: directory containing the OpenAI-compatible scorer.
- `urgency.providers.openai.embedding.model.name`: OpenAI embedding model name, defaulting to `text-embedding-3-small`.
- `urgency.providers.openai.embedding.dimensions`: optional OpenAI embedding dimensions override; keep it aligned with the selected scorer input size.
- `openai.api-key`: optional config key. Prefer `OPENAI_API_KEY` for local development.

## Build

Use the repository JDK 25+ and Maven.

```bash
mvn -q compile
```

## Run

```bash
java -jar target/urgency-mcp.jar
```

Select a provider explicitly:

```bash
java -Durgency.provider=local -jar target/urgency-mcp.jar
java -Durgency.provider=openai -Dopenai.api-key="$OPENAI_API_KEY" -jar target/urgency-mcp.jar
```

Inference initialization fails if the selected provider is unknown, the selected scorer file is missing, or OpenAI mode lacks an API key. The MCP HTTP server creates inference lazily on the first `tools/call`, so discovery, ping, and tool listing can still work before the scorer is loaded.

The packaged Helidon runtime installs a narrow Java serialization allow-list before startup so DeepNetts can deserialize checked-in `.dnet` scorer files. If you provide your own `jdk.serialFilter` or `helidon.serialFilter.pattern`, include the DeepNetts model classes and a final `!*` reject rule.

## MCP Conformance

The project wires the official MCP conformance runner from `modelcontextprotocol/conformance`. The Maven profile starts this Helidon server and runs selected server scenarios against the real `/urgency` endpoint only when `MCP_CONFORMANCE_ENABLED=true` is set. Without that flag, the conformance script exits successfully without starting the server.

The default scenario list covers stateless protocol behavior only: discovery, ping, and tool listing. It intentionally excludes `tools-call-simple-text` because the real urgency tool performs domain scoring and requires a `phrase` argument. Other scenarios can still be requested explicitly with `MCP_CONFORMANCE_SCENARIOS` when validating broader server behavior.

```bash
MCP_CONFORMANCE_ENABLED=true mvn -Pconformance verify

# Override the focused scenario list when needed.
MCP_CONFORMANCE_ENABLED=true MCP_CONFORMANCE_SCENARIOS="server-discover ping tools-list" mvn -Pconformance verify
```

To run the conformance CLI manually:

```bash
mvn -q -DskipTests package
java -jar target/urgency-mcp.jar &
npx @modelcontextprotocol/conformance server --url http://localhost:9090/urgency --scenario tools-list
```

## ai-triage config

Use these settings in `ai-triage` when switching to MCP mode:

```properties
quarkus.langchain4j.mcp.urgency.transport-type=http
quarkus.langchain4j.mcp.urgency.url=http://localhost:9090/urgency
quarkus.langchain4j.mcp.urgency.tool-execution-timeout=4s
```

Start this server before ai-triage so the MCP client can connect on first request.
