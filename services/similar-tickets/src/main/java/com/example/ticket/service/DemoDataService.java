package com.example.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class DemoDataService {

    private static final Logger log = Logger.getLogger(DemoDataService.class.getName());
    private static final int PROGRESS_INTERVAL = 10;

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

        List<Map<String, Object>> allTickets = new ArrayList<>();
        for (String file : DEMO_DATA_FILES) {
            List<Map<String, Object>> tickets = loadTicketsFromFile(file);
            allTickets.addAll(tickets.stream()
                    .filter(t -> t.get("ticketType") != null && t.get("originalRequest") != null)
                    .toList());
        }
        int totalToLoad = allTickets.size();

        long nextDemoTicketId = 1L;
        for (int i = 0; i < allTickets.size(); i++) {
            Map<String, Object> ticket = allTickets.get(i);
            Long ticketId = nextDemoTicketId++;
            String ticketType = ticket.get("ticketType").toString();
            String originalRequest = ticket.get("originalRequest").toString();

            float[] embedding = embeddingService.embed(originalRequest);
            vectorService.upsertTicket(ticketId, ticketType, originalRequest, embedding);

            int processed = i + 1;
            if (processed % PROGRESS_INTERVAL == 0) {
                String msg = "Processed tickets " + processed + "/" + totalToLoad;
                log.info(msg);
                logService.addLog(msg, "demo-data");
            }
        }
        logService.addLog("Demo data load complete: " + totalToLoad + " tickets loaded", "demo-data");
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
