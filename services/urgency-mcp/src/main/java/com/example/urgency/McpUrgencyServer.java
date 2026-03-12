package com.example.urgency;

import com.example.urgency.service.UrgencyInferenceService;
import java.util.List;
import java.util.logging.Logger;

import io.helidon.extensions.mcp.server.Mcp;
import io.helidon.extensions.mcp.server.McpToolContent;
import io.helidon.extensions.mcp.server.McpToolContents;

@Mcp.Path("/urgency")
@Mcp.Server("helidon-mcp-urgency")
class McpUrgencyServer {

    private static final Logger log = Logger.getLogger(McpUrgencyServer.class.getName());
    private static final UrgencyInferenceService inference = new UrgencyInferenceService();

    @Mcp.Tool("Get urgency score (0–10) for a support ticket complaint")
    List<McpToolContent> getUrgency(@Mcp.Description("complaint text to score") String phrase) {
        log.info("MCP server called: getUrgency(phrase=\"" + phrase + "\")");
        double score = inference.score(phrase);
        String result = Double.toString(score);
        log.info("MCP server returning urgency score: " + result);
        return List.of(McpToolContents.textContent(result));
    }
}
