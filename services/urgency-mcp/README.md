# Urgency MCP Server

MCP server that exposes urgency inference for support ticket phrases over **stdio**. Uses the trained model from `urgency-training-pipeline`.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/urgency-mcp-1.0.0-SNAPSHOT-runner.jar
```

## Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "urgency": {
      "command": "java",
      "args": ["-jar", "/path/to/urgency-mcp/target/urgency-mcp-1.0.0-SNAPSHOT-runner.jar"]
    }
  }
}
```

## Tools

- **getUrgency(phrase)**: Returns urgency score 0–10 for a support ticket phrase. Higher = more urgent.
