# Urgency MCP Server

Helidon-based Java MCP server that exposes urgency scoring for support ticket text. The MCP endpoint is `http://localhost:9090/urgency`, and the server exposes a single tool, `getUrgency`, which returns an urgency score from `0.0` to `10.0`.

The MCP surface is declared with Helidon annotations:

- `@Mcp.Path("/urgency")` defines the endpoint.
- `@Mcp.Server("helidon-mcp-urgency")` identifies the server.
- `@Mcp.Tool` exposes the urgency-scoring operation.

Behind that MCP boundary, the server delegates scoring to a local inference stack built from:

- an embedding provider;
- a provider-matched DeepNetts scorer file;
- a thin `UrgencyInferenceService` that validates input and delegates scoring.

## Runtime Modes

The server supports two embedding paths, both of which score locally with DeepNetts:

- `local`: MiniLM embeddings generated locally through DJL, then scored with `model-scorer-local.dnet`.
- `openai`: OpenAI embeddings, defaulting to `text-embedding-3-small`, then scored locally with `model-scorer-openai.dnet`.

OpenAI is the external-provider example in this repository, not a requirement of the design. In both modes, the final urgency score comes from the local `.dnet` scorer file.

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
        name: sentence-transformers/all-MiniLM-L6-v2
        location: ../urgency/model
        dimensions: 384
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
- `urgency.providers.local.model.name`: local DeepNetts scorer file name.
- `urgency.providers.local.model.location`: directory containing the local scorer.
- `urgency.providers.local.embedding.name`: local embedding model identifier, currently `sentence-transformers/all-MiniLM-L6-v2`.
- `urgency.providers.local.embedding.location`: local embedding model location value carried in config.
- `urgency.providers.local.embedding.dimensions`: local embedding vector width, currently `384`.
- `urgency.providers.openai.model.name`: OpenAI-compatible DeepNetts scorer file name.
- `urgency.providers.openai.model.location`: directory containing the OpenAI-compatible scorer.
- `urgency.providers.openai.embedding.model.name`: OpenAI embedding model name, defaulting to `text-embedding-3-small`.
- `urgency.providers.openai.embedding.dimensions`: optional OpenAI embedding dimensions override; keep it aligned with the selected scorer.
- `openai.api-key`: optional config key. `OPENAI_API_KEY` can also be used.

## Model Compatibility

The `.dnet` scorer files used by `urgency-mcp` come from `services/urgency-training-pipeline`. They are provider-specific artifacts, not generic model weights.

Current matched pairs are:

```text
sentence-transformers/all-MiniLM-L6-v2 -> model-scorer-local.dnet
OpenAI text-embedding-3-small          -> model-scorer-openai.dnet
```

Do not reuse a scorer across incompatible embedding spaces.

## Build

Use Java 25+ and Maven.

```bash
MAVEN_OPTS=--enable-preview mvn clean package
```

Note: this module uses Java preview features. Maven and the JDK used to build and test it must support the configured preview release.

## Run

```bash
java --enable-preview -jar target/urgency-mcp.jar
```

Select a provider explicitly when needed:

```bash
java --enable-preview -Durgency.provider=local -jar target/urgency-mcp.jar
java --enable-preview -Durgency.provider=openai -Dopenai.api-key="$OPENAI_API_KEY" -jar target/urgency-mcp.jar
```

Inference initialization fails if:

- the selected provider is unknown;
- the selected scorer file is missing;
- OpenAI mode is selected without an API key.

`McpUrgencyServer` initializes the scorer lazily on first tool call. That means the MCP endpoint can come up before the full inference stack is exercised.


## Health and Observability

Helidon health and observability support is enabled through `helidon-webserver-observe-health` and `helidon-health-checks`. Operational health is exposed separately from MCP protocol traffic:

```bash
curl http://localhost:9090/observe/health
curl http://localhost:9090/observe/health/ready
```

These endpoints are for service liveness/readiness checks. MCP `ping` remains handled as protocol traffic on `/urgency`.

## MCP Behavior

The `/urgency` endpoint supports two protocol paths:

- Requests without `MCP-Protocol-Version: 2026-07-28` continue through Helidon's annotation-based MCP server support for the earlier MCP specifications.
- Requests with `MCP-Protocol-Version: 2026-07-28` are handled by a stateless HTTP adapter before the generated Helidon MCP route.

The 2026 adapter does not require `initialize`, does not create or return `Mcp-Session-Id`, and rejects any request that carries a session header. Each request must include:

- `MCP-Protocol-Version: 2026-07-28`
- `Mcp-Method: <json-rpc-method>`
- `Mcp-Name: getUrgency` for `tools/call`

The adapter supports `server/discover`, `ping`, `tools/list`, and `tools/call`. It rejects requests where `Mcp-Method` disagrees with the JSON-RPC body method, or where `Mcp-Name` disagrees with `params.name` for `tools/call`.

`tools/list` advertises the single `getUrgency` tool with JSON Schema 2020-12 input schema requiring `phrase`, plus cache metadata:

- `ttlMs: 300000`
- `cacheScope: "public"`

`tools/call` returns both text content and numeric `structuredContent` so older text-oriented clients and typed 2026 clients can consume the urgency score.

The `getUrgency` tool is declared with these behavior hints:

- `readOnlyHint = true`
- `destructiveHint = false`
- `idempotentHint = true`
- `openWorldHint = false`

MCP Apps, Tasks, Roots, Sampling, Logging, and OAuth/OIDC authorization are not implemented by this optional local urgency-scoring service. 
OAuth/OIDC hardening should be handled at the gateway or by a later MCP auth integration if this endpoint becomes a protected remote service.

Example stateless discovery request:

```bash
curl -X POST http://localhost:9090/urgency \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: server/discover' \
  -d '{"jsonrpc":"2.0","id":1,"method":"server/discover"}'
```

Example stateless tool call:

```bash
curl -X POST http://localhost:9090/urgency \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/call' \
  -H 'Mcp-Name: getUrgency' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"getUrgency","arguments":{"phrase":"patient cannot access insulin refill"}}}'
```

## MCP Conformance

The conformance script starts this server in local mode. By default it targets the stateless MCP `2026-07-28` adapter, but the published `@modelcontextprotocol/conformance` runner may not yet include draft-only scenarios such as `server-discover`.
To avoid unknown-scenario failures, the script separates local draft checks from official runner scenarios.

Run the default stateless draft check with:

```bash
MCP_CONFORMANCE_ENABLED=true MAVEN_OPTS=--enable-preview mvn -Pconformance verify
```

The default `2026-07-28` run performs local checks for:

- `server-discover`
- `tools-list`
- `reject-session-header`

These defaults are controlled with:

```text
MCP_CONFORMANCE_PROTOCOL_VERSION=2026-07-28
MCP_DRAFT_CHECKS="server-discover tools-list reject-session-header"
MCP_CONFORMANCE_SCENARIOS="ping tools-list"
```

If a newer published conformance runner adds draft scenarios, pass those scenario names through `MCP_CONFORMANCE_SCENARIOS` explicitly.

To run the Helidon annotation-server path with scenarios available in the published runner, override the protocol version and scenarios explicitly:

```bash
MCP_CONFORMANCE_ENABLED=true \
MCP_CONFORMANCE_PROTOCOL_VERSION=2025-06-18 \
MCP_CONFORMANCE_SCENARIOS="server-initialize ping tools-list" \
MAVEN_OPTS=--enable-preview mvn -Pconformance verify
```

In the stateless flow, the readiness probe uses `server/discover` with `MCP-Protocol-Version: 2026-07-28` and `Mcp-Method: server/discover`. In the older compatibility flow, readiness uses `initialize`.

## ai-triage Configuration

Use these settings in `ai-triage` when switching to MCP mode:

```properties
quarkus.langchain4j.mcp.urgency.transport-type=http
quarkus.langchain4j.mcp.urgency.url=http://localhost:9090/urgency
quarkus.langchain4j.mcp.urgency.tool-execution-timeout=4s
```

Start this server before `ai-triage` so the MCP client can connect on first request.
