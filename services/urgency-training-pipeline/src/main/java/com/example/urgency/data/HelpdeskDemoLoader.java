package com.example.urgency.data;

import com.example.urgency.model.Ticket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads tickets from helpdesk demo-data format.
 * Demo files: services/helpdesk/src/main/resources/demo-data/demo-tickets-*.json
 *
 * Format: originalRequest (text), urgencyScore (0–10) → normalized to 0–1.
 */
public final class HelpdeskDemoLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double URGENCY_SCALE = 10.0;

    private HelpdeskDemoLoader() {}

    /**
     * Load all demo-tickets-*.json from a directory.
     */
    public static List<Ticket> loadFromDirectory(Path dir) throws Exception {
        List<Ticket> all = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "demo-tickets-*.json")) {
            for (Path file : stream) {
                all.addAll(loadFromFile(file));
            }
        }
        return all;
    }

    /**
     * Load from a single JSON file (helpdesk format).
     */
    public static List<Ticket> loadFromFile(Path file) throws Exception {
        JsonNode root = MAPPER.readTree(file.toFile());
        List<Ticket> tickets = new ArrayList<>();
        for (JsonNode node : root) {
            String text = node.path("originalRequest").asText();
            double urgencyScore = node.path("urgencyScore").asDouble();
            double urgency = Math.min(1.0, Math.max(0.0, urgencyScore / URGENCY_SCALE));
            String id = String.valueOf(node.path("id").asInt());
            tickets.add(new Ticket(id, text, urgency));
        }
        return tickets;
    }
}
