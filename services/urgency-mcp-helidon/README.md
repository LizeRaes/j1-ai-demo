# Urgency MCP Server (Helidon HTTP)

MCP server exposing urgency inference for support ticket phrases over **HTTP** (Streamable HTTP transport). Uses the trained model from `urgency-training-pipeline`.

Runs on port **9000**. ai-triage connects via `streamable-http` transport.

## Build

```bash
mvn clean package
```

## Run

```bash
java -jar target/urgency-mcp-helidon.jar
```

Server listens at `http://localhost:9000/urgency`.

## ai-triage config

```properties
quarkus.langchain4j.mcp.urgency.transport-type=streamable-http
quarkus.langchain4j.mcp.urgency.url=http://localhost:9000/urgency
```

Start this server **before** ai-triage so the MCP client can connect on first request.
