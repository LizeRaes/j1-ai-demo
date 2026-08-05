package com.example.urgency.mcp;

import java.util.List;
import java.util.Objects;

import com.example.urgency.McpUrgencyServer;

import io.helidon.http.Status;
import io.helidon.json.JsonObject;
import io.helidon.service.registry.Service;

@Service.Singleton
public final class StatelessMcpProtocolHandler {

    public static final String PROTOCOL_VERSION = "2026-07-28";

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final String METHOD_HEADER = "Mcp-Method";
    private static final String TOOL_NAME = "getUrgency";
    private static final String SERVER_NAME = "helidon-mcp-urgency";
    private static final String NAME_HEADER = "Mcp-Name";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final int CACHE_TTL_MS = 300_000;
    private static final String CACHE_SCOPE = "public";
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;

    private final McpUrgencyServer urgencyServer;

    @Service.Inject
    public StatelessMcpProtocolHandler(McpUrgencyServer urgencyServer) {
        this.urgencyServer = Objects.requireNonNull(urgencyServer, "urgencyServer");
    }

    public McpProtocolResponse handle(McpProtocolRequest request) {
        var id = request.body().value("id").orElse(null);
        Validation validation = validateEnvelope(request);
        if (validation != null) {
            return error(id, validation.status(), validation.code(), validation.message());
        }

        return switch (request.header(METHOD_HEADER)) {
            case "server/discover" -> ok(id, discoveryResult());
            case "ping" -> ok(id, JsonObject.empty());
            case "tools/list" -> ok(id, toolsListResult());
            case "tools/call" -> callTool(id, request);
            default -> error(id, Status.BAD_REQUEST_400, METHOD_NOT_FOUND, "Unsupported MCP method");
        };
    }

    private Validation validateEnvelope(McpProtocolRequest request) {
        if (!PROTOCOL_VERSION.equals(request.header(PROTOCOL_VERSION_HEADER))) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Unsupported MCP protocol version");
        }
        if (request.header(SESSION_HEADER) != null) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST,
                    "Mcp-Session-Id is not allowed for MCP 2026-07-28 requests");
        }
        String headerMethod = request.header(METHOD_HEADER);
        if (headerMethod == null || headerMethod.isBlank()) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Mcp-Method header is required");
        }
        String bodyMethod = request.body().stringValue("method", "");
        if (bodyMethod.isBlank()) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "JSON-RPC method is required");
        }
        if (!headerMethod.equals(bodyMethod)) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Mcp-Method header must match JSON-RPC method");
        }
        if (!JSON_RPC_VERSION.equals(request.body().stringValue("jsonrpc", ""))) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "jsonrpc must be 2.0");
        }
        if ("tools/call".equals(headerMethod)) {
            return validateToolName(request);
        }
        return null;
    }

    private Validation validateToolName(McpProtocolRequest request) {
        String headerName = request.header(NAME_HEADER);
        if (!TOOL_NAME.equals(headerName)) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_PARAMS, "Mcp-Name must be getUrgency for tools/call");
        }
        JsonObject params = request.body().objectValue("params", JsonObject.empty());
        if (!TOOL_NAME.equals(params.stringValue("name", ""))) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_PARAMS, "tools/call params.name must be getUrgency");
        }
        return null;
    }

    private McpProtocolResponse callTool(io.helidon.json.JsonValue id, McpProtocolRequest request) {
        JsonObject params = request.body().objectValue("params", JsonObject.empty());
        JsonObject arguments = params.objectValue("arguments", JsonObject.empty());
        String text = arguments.stringValue("phrase", "");
        if (text.isBlank()) {
            return error(id, Status.BAD_REQUEST_400, INVALID_PARAMS, "tools/call requires non-blank arguments.phrase");
        }

        double score = urgencyServer.score(text);
        JsonObject content = JsonObject.builder()
                .set("type", "text")
                .set("text", Double.toString(score))
                .build();
        JsonObject result = JsonObject.builder()
                .setValues("content", List.of(content))
                .set("structuredContent", score)
                .build();
        return ok(id, result);
    }

    private static JsonObject discoveryResult() {
        return JsonObject.builder()
                .set("resultType", "complete")
                .setStrings("supportedVersions", List.of(PROTOCOL_VERSION))
                .setStrings("protocolVersions", List.of(PROTOCOL_VERSION))
                .set("capabilities", builder -> builder.set("tools", JsonObject.empty()))
                .set("serverInfo", builder -> builder
                        .set("name", SERVER_NAME)
                        .set("version", "1.0.0"))
                .set("ttlMs", CACHE_TTL_MS)
                .set("cacheScope", CACHE_SCOPE)
                .build();
    }

    private static JsonObject toolsListResult() {
        JsonObject tool = JsonObject.builder()
                .set("name", TOOL_NAME)
                .set("title", "Get urgency score")
                .set("description", "Get urgency score (0-10) for a support ticket complaint")
                .set("inputSchema", inputSchema())
                .set("annotations", annotations())
                .build();
        return JsonObject.builder()
                .setValues("tools", List.of(tool))
                .set("ttlMs", CACHE_TTL_MS)
                .set("cacheScope", CACHE_SCOPE)
                .build();
    }

    private static JsonObject inputSchema() {
        return JsonObject.builder()
                .set("$schema", "https://json-schema.org/draft/2020-12/schema")
                .set("type", "object")
                .set("properties", builder -> builder.set("phrase", phrase -> phrase
                        .set("type", "string")
                        .set("description", "complaint text to score")))
                .setStrings("required", List.of("phrase"))
                .set("additionalProperties", false)
                .build();
    }

    private static JsonObject annotations() {
        return JsonObject.builder()
                .set("readOnlyHint", true)
                .set("destructiveHint", false)
                .set("idempotentHint", true)
                .set("openWorldHint", false)
                .build();
    }

    private static McpProtocolResponse ok(io.helidon.json.JsonValue id, JsonObject result) {
        JsonObject body = responseBody(id)
                .set("result", result)
                .build();
        return new McpProtocolResponse(Status.OK_200, body);
    }

    private static McpProtocolResponse error(io.helidon.json.JsonValue id, Status status, int code, String message) {
        JsonObject body = responseBody(id)
                .set("error", builder -> builder
                        .set("code", code)
                        .set("message", message))
                .build();
        return new McpProtocolResponse(status, body);
    }

    private static JsonObject.Builder responseBody(io.helidon.json.JsonValue id) {
        JsonObject.Builder body = JsonObject.builder()
                .set("jsonrpc", JSON_RPC_VERSION);
        if (id == null) {
            body.setNull("id");
        } else {
            body.set("id", id);
        }
        return body;
    }

    public static McpProtocolResponse parseError() {
        return error(null, Status.BAD_REQUEST_400, PARSE_ERROR, "Request body must be a JSON-RPC object");
    }

    private record Validation(Status status, int code, String message) { }
}
