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
mvn clean package
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

## MCP Behavior

The current server uses Helidon's annotation-based MCP server support and stays aligned with the earlier MCP protocol line supported by the extension, centered on the `2025-06-18` family.

The `getUrgency` tool is declared with these behavior hints:

- `readOnlyHint = true`
- `destructiveHint = false`
- `idempotentHint = true`
- `openWorldHint = false`

In practice, that tells MCP clients that the tool reads input, does not mutate state, is safe to retry, and operates within a narrow application concern.

## MCP Conformance

The conformance script uses the official MCP conformance runner and starts this server in local mode. By default it runs focused server scenarios against the real `/urgency` endpoint on the older MCP line:

- `server-initialize`
- `ping`
- `tools-list`

Run it with:

```bash
MCP_CONFORMANCE_ENABLED=true mvn -Pconformance verify
```

Override the default scenarios when needed:

```bash
MCP_CONFORMANCE_ENABLED=true MCP_CONFORMANCE_SCENARIOS="server-initialize ping tools-list" mvn -Pconformance verify
```

The default list intentionally excludes tool-call scenarios because the real urgency tool performs domain scoring and requires a `phrase` argument.

## ai-triage Configuration

Use these settings in `ai-triage` when switching to MCP mode:

```properties
quarkus.langchain4j.mcp.urgency.transport-type=http
quarkus.langchain4j.mcp.urgency.url=http://localhost:9090/urgency
quarkus.langchain4j.mcp.urgency.tool-execution-timeout=4s
```

Start this server before `ai-triage` so the MCP client can connect on first request.
