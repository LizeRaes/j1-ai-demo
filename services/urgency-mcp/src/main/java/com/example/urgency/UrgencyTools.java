package com.example.urgency;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrgencyTools {

    private static final Logger log = LoggerFactory.getLogger(UrgencyTools.class);

    @Tool(description = "Get urgency score (0–10) for a support ticket complaint")
    public String getUrgency(
            @ToolArg(description = "complaint text to score") String phrase) {
        log.info("MCP server called: getUrgency(phrase=\"{}\")", phrase);
        // TODO: Load model from urgency-training-pipeline and run inference.
        String result = "7";
        log.info("MCP server returning urgency score: {}", result);
        return result;
    }
}
