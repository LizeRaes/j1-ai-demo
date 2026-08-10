package com.example.urgency;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.example.urgency.service.UrgencyScorer;

import io.helidon.extensions.mcp.server.Mcp;
import io.helidon.extensions.mcp.server.McpToolResult;
import io.helidon.service.registry.Service;

@Mcp.Path(McpUrgencyServer.MCP_PATH)
@Mcp.Server(McpUrgencyServer.MCP_SERVER_NAME)
@Mcp.Stateless
public final class McpUrgencyServer {

    static final String MCP_PATH = "/urgency";
    public static final String MCP_SERVER_NAME = "helidon-mcp-urgency";
    private static final Logger log = Logger.getLogger(McpUrgencyServer.class.getName());

    private final Supplier<UrgencyScorer> scorerSupplier;

    @Service.Inject
    public McpUrgencyServer(Supplier<UrgencyScorer> scorerSupplier) {
        this.scorerSupplier = Objects.requireNonNull(scorerSupplier, "urgency scorer supplier must not be null");
    }

    @Mcp.Tool(value = "Get urgency score (0-10) for a support ticket complaint",
              title = "Get urgency score",
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false)
    McpToolResult getUrgency(@Mcp.Description("complaint text to score") String phrase) {
        log.info(() -> "MCP server called: getUrgency(phrase=\"" + phrase + "\")");
        double score = score(phrase);
        String result = Double.toString(score);
        log.info(() -> "MCP server returning urgency score: " + result);
        return McpToolResult.create(result);
    }

    public double score(String phrase) {
        return scorerSupplier.get().score(phrase);
    }
}
