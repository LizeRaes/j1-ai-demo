package com.example.urgency;

import java.util.concurrent.atomic.AtomicReference;

import com.example.urgency.service.UrgencyScorer;

import io.helidon.extensions.mcp.server.Mcp;
import io.helidon.extensions.mcp.server.McpToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpUrgencyServerTest {

    @Test
    void declaresMcpPathEndpoint() {
        Mcp.Path path = McpUrgencyServer.class.getAnnotation(Mcp.Path.class);

        assertNotNull(path);
        assertEquals(McpUrgencyServer.MCP_PATH, path.value());
    }

    @Test
    void declaresGetUrgencyToolMetadata() throws Exception {
        Mcp.Tool tool = McpUrgencyServer.class
                .getDeclaredMethod("getUrgency", String.class)
                .getAnnotation(Mcp.Tool.class);

        assertNotNull(tool);
        assertEquals("Get urgency score (0-10) for a support ticket complaint", tool.value());
        assertEquals("Get urgency score", tool.title());
        assertTrue(tool.readOnlyHint());
        assertFalse(tool.destructiveHint());
        assertTrue(tool.idempotentHint());
        assertFalse(tool.openWorldHint());
    }

    @Test
    void getUrgencyReturnsTextScore() {
        AtomicReference<String> complaint = new AtomicReference<>();
        UrgencyScorer scorer = value -> {
            complaint.set(value);
            return 7.5;
        };
        McpUrgencyServer server = McpUrgencyServer.withScorerSupplier(() -> scorer);

        McpToolResult result = server.getUrgency("patient cannot access billing portal");

        assertEquals("patient cannot access billing portal", complaint.get());
        assertEquals(1, result.textContents().size());
        assertEquals("7.5", result.textContents().getFirst().text());
        assertFalse(result.error());
    }

    @Test
    void requiresScorerSupplier() {
        assertThrows(NullPointerException.class, () -> McpUrgencyServer.withScorerSupplier(null));
    }
}
