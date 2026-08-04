package com.example.urgency.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.urgency.McpUrgencyServer;

import io.helidon.http.Status;
import io.helidon.service.registry.Service;

@Service.Singleton
public final class StatelessMcpProtocolHandler {

    public static final String PROTOCOL_VERSION = "2026-07-28";

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final String METHOD_HEADER = "Mcp-Method";
    private static final String NAME_HEADER = "Mcp-Name";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String SERVER_NAME = "helidon-mcp-urgency";
    private static final String TOOL_NAME = "getUrgency";
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
        Object id = request.body().get("id");
        Validation validation = validateEnvelope(request);
        if (validation != null) {
            return error(id, validation.status(), validation.code(), validation.message());
        }

        return switch (request.header(METHOD_HEADER)) {
            case "server/discover" -> ok(id, discoveryResult());
            case "ping" -> ok(id, Map.of());
            case "tools/list" -> ok(id, toolsListResult());
            case "tools/call" -> callTool(id, request);
            default -> error(id, Status.BAD_REQUEST_400, METHOD_NOT_FOUND, "Unsupported MCP method");
        };
    }

    private Validation validateEnvelope(McpProtocolRequest request) {
        if (request.header(SESSION_HEADER) != null) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Mcp-Session-Id is not supported by MCP 2026-07-28");
        }
        if (!PROTOCOL_VERSION.equals(request.header(PROTOCOL_VERSION_HEADER))) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Unsupported MCP protocol version");
        }
        String headerMethod = request.header(METHOD_HEADER);
        if (headerMethod == null || headerMethod.isBlank()) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Mcp-Method header is required");
        }
        Object method = request.body().get("method");
        if (!(method instanceof String bodyMethod) || bodyMethod.isBlank()) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "JSON-RPC method is required");
        }
        if (!headerMethod.equals(bodyMethod)) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_REQUEST, "Mcp-Method header must match JSON-RPC method");
        }
        Object jsonRpc = request.body().get("jsonrpc");
        if (!JSON_RPC_VERSION.equals(jsonRpc)) {
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
        Map<String, Object> params = objectMap(request.body().get("params"));
        if (params == null || !TOOL_NAME.equals(params.get("name"))) {
            return new Validation(Status.BAD_REQUEST_400, INVALID_PARAMS, "tools/call params.name must be getUrgency");
        }
        return null;
    }

    private McpProtocolResponse callTool(Object id, McpProtocolRequest request) {
        Map<String, Object> params = objectMap(request.body().get("params"));
        Map<String, Object> arguments = params == null ? null : objectMap(params.get("arguments"));
        Object phrase = arguments == null ? null : arguments.get("phrase");
        if (!(phrase instanceof String text) || text.isBlank()) {
            return error(id, Status.BAD_REQUEST_400, INVALID_PARAMS, "tools/call requires non-blank arguments.phrase");
        }

        double score = urgencyServer.score(text);
        return ok(id, Map.of(
                "content", List.of(Map.of("type", "text", "text", Double.toString(score))),
                "structuredContent", score));
    }

    private static Map<String, Object> discoveryResult() {
        return Map.of(
                "protocolVersions", List.of(PROTOCOL_VERSION),
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", SERVER_NAME, "version", "1.0.0"),
                "ttlMs", CACHE_TTL_MS,
                "cacheScope", CACHE_SCOPE);
    }

    private static Map<String, Object> toolsListResult() {
        return Map.of(
                "tools", List.of(Map.of(
                        "name", TOOL_NAME,
                        "title", "Get urgency score",
                        "description", "Get urgency score (0-10) for a support ticket complaint",
                        "inputSchema", inputSchema(),
                        "annotations", Map.of(
                                "readOnlyHint", true,
                                "destructiveHint", false,
                                "idempotentHint", true,
                                "openWorldHint", false))),
                "ttlMs", CACHE_TTL_MS,
                "cacheScope", CACHE_SCOPE);
    }

    private static Map<String, Object> inputSchema() {
        return Map.of(
                "$schema", "https://json-schema.org/draft/2020-12/schema",
                "type", "object",
                "properties", Map.of(
                        "phrase", Map.of(
                                "type", "string",
                                "description", "complaint text to score")),
                "required", List.of("phrase"),
                "additionalProperties", false);
    }

    private static McpProtocolResponse ok(Object id, Map<String, Object> result) {
        Map<String, Object> body = responseBody(id);
        body.put("result", result);
        return new McpProtocolResponse(Status.OK_200, body);
    }

    private static McpProtocolResponse error(Object id, Status status, int code, String message) {
        Map<String, Object> body = responseBody(id);
        body.put("error", Map.of("code", code, "message", message));
        return new McpProtocolResponse(status, body);
    }

    private static Map<String, Object> responseBody(Object id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", JSON_RPC_VERSION);
        body.put("id", id);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    public static McpProtocolResponse parseError() {
        return error(null, Status.BAD_REQUEST_400, PARSE_ERROR, "Request body must be a JSON-RPC object");
    }

    private record Validation(Status status, int code, String message) {
    }
}
