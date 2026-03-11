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