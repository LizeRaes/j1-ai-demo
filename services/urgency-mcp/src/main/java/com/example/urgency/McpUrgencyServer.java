package com.example.urgency;

import java.util.List;
import java.util.logging.Logger;

import io.helidon.extensions.mcp.server.Mcp;
import io.helidon.extensions.mcp.server.McpToolContent;
import io.helidon.extensions.mcp.server.McpToolContents;

@Mcp.Path("/urgency")
@Mcp.Server("helidon-mcp-urgency")
class McpUrgencyServer {

    private static final Logger log = Logger.getLogger(McpUrgencyServer.class.getName());

    @Mcp.Tool("Get urgency score (0–10) for a support ticket complaint")
    List<McpToolContent> getUrgency(@Mcp.Description("complaint text to score") String phrase) {
        log.info("MCP server called: getUrgency(phrase=\"" + phrase + "\")");
        // TODO: Load model from urgency-training-pipeline and run inference. x10. round to int.
        String result = "7";
        log.info("MCP server returning urgency score: " + result);
        return List.of(McpToolContents.textContent(result));
    }
}
