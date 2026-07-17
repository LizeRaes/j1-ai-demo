package com.example.urgency.mcp;

import java.util.Arrays;
import java.util.Optional;

public enum McpMethod {
    SERVER_DISCOVER("server/discover"),
    PING("ping"),
    TOOLS_LIST("tools/list"),
    TOOLS_CALL("tools/call");

    private final String methodName;

    McpMethod(String methodName) {
        this.methodName = methodName;
    }

    public String methodName() {
        return methodName;
    }

    boolean requiresToolName() {
        return this == TOOLS_CALL;
    }

    static Optional<McpMethod> find(String methodName) {
        return Arrays.stream(values())
                .filter(method -> method.methodName.equals(methodName))
                .findFirst();
    }
}
