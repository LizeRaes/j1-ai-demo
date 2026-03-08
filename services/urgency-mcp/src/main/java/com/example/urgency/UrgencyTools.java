package com.example.urgency;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class UrgencyTools {

    @Tool(description = "Get urgency score (0–10) for a support ticket complaint")
    public String getUrgency(
            @ToolArg(description = "complaint text to score") String phrase) {
        // TODO: Load model from urgency-training-pipeline and run inference.
        return String.valueOf(7.5);
    }
}
