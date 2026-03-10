# Urgency MCP Server (Helidon HTTP)

MCP server exposing urgency inference for support ticket phrases over **HTTP** (Streamable HTTP/SSE transport). Uses the trained model from `urgency-training-pipeline`.

Runs on port **9000**. ai-triage connects via `streamable-http` transport.

## Build

```bash
mvn clean package
```

## Run

```bash
java -jar target/urgency-mcp.jar
```

Server listens at `http://localhost:9090/urgency`.

## ai-triage config

Use these settings in `ai-triage` project when switching to MCP mode:

```properties
quarkus.langchain4j.mcp.urgency.transport-type=http
quarkus.langchain4j.mcp.urgency.url=http://localhost:9090/urgency
quarkus.langchain4j.mcp.urgency.tool-execution-timeout=4s
```

Start this server **before** ai-triage so the MCP client can connect on first request.
