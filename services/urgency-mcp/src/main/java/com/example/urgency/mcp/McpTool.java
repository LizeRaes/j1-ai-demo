package com.example.urgency.mcp;

public enum McpTool {
    GET_URGENCY("getUrgency", "Get urgency score (0-10) for a support ticket complaint", "complaint text to score");

    private final String toolName;
    private final String description;
    private final String phraseDescription;

    McpTool(String toolName, String description, String phraseDescription) {
        this.toolName = toolName;
        this.description = description;
        this.phraseDescription = phraseDescription;
    }

    public String toolName() {
        return toolName;
    }

    String description() {
        return description;
    }

    String phraseDescription() {
        return phraseDescription;
    }
}
