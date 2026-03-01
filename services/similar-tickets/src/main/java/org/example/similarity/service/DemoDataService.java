package org.example.similarity.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DemoDataService {

    VectorService vectorService;

    EmbeddingService embeddingService;

    LogService logService;

    private static final String[] DEMO_DATA_FILES = {
            "demo-data/demo-tickets-access-other.json",
            "demo-data/demo-tickets-billing.json",
            "demo-data/demo-tickets-engineering.json",
            "demo-data/demo-tickets-scheduling.json"
    };

    public DemoDataService(VectorService vectorService, EmbeddingService embeddingService, LogService logService) {
        this.vectorService = vectorService;
        this.embeddingService = embeddingService;
        this.logService = logService;
    }

    public void loadDemoData() {
        logService.addLog("Starting demo data load...", "demo-data");
        vectorService.deleteAllTickets();
        logService.addLog("Cleared existing Oracle AI Database data", "demo-data");

        int totalLoaded = 0;
        long nextDemoTicketId = 1L;
        for (String file : DEMO_DATA_FILES) {
            List<Map<String, Object>> tickets = loadTicketsFromFile(file);

            for (Map<String, Object> ticket : tickets) {
                Long ticketId = nextDemoTicketId++;
                String ticketType = ticket.get("ticketType").toString();
                String originalRequest = ticket.get("originalRequest").toString();

                if (ticketType != null && originalRequest != null) {
                    float[] embedding = embeddingService.embed(originalRequest);
                    vectorService.upsertTicket(ticketId, ticketType, originalRequest, embedding);
                    totalLoaded++;
                }
            }
            logService.addLog("Loaded " + tickets.size() + " tickets from " + file, "demo-data");
        }
        logService.addLog("Demo data load complete: " + totalLoaded + " tickets loaded", "demo-data");
    }

    private List<Map<String, Object>> loadTicketsFromFile(String filePath) {

        try {
            InputStream inputStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(filePath);

            if (Objects.isNull(inputStream)) {
                throw new RuntimeException("Demo data file not found: " + filePath);
            }

            ObjectMapper objectMapper = new ObjectMapper();

            List<Map<String, Object>> tickets = objectMapper.readValue(
                    inputStream,
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionType(List.class, Map.class)
            );

            return tickets != null ? tickets : List.of();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load demo data from " + filePath, e);
        }
    }
}
