package com.example.ticket.service;

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import com.example.ticket.dto.*;

import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public class SimilarityService implements HttpService {

    private final int defaultZoom;

    private final LogService logService;
    private final TicketStore ticketStore;
    private final EmbeddingService embeddingService;
    private final VectorService vectorService;

    public SimilarityService(Config config, EmbeddingService embeddingService, VectorService vectorService) {
       defaultZoom = config.asInt().orElse(100);
       this.logService = new LogService();
       this.ticketStore = new TicketStore();
       this.embeddingService = embeddingService;
       this.vectorService = vectorService;
    }

    @Override
    public void beforeStart() {
        HttpService.super.beforeStart();
        String demoDataFlag = System.getProperty("DemoData");
        DemoDataService demoDataService = new DemoDataService(vectorService, embeddingService, logService);

        if (Boolean.parseBoolean(demoDataFlag)) {
            logService.addLog("DemoData flag detected. Loading demo data and wiping Oracle DB...", "startup");
            try {
                demoDataService.loadDemoData();
                logService.addLog("Demo data loaded successfully", "startup");
            } catch (Exception e) {
                logService.addLog("Failed to load demo data: " + e.getMessage(), "startup");
                throw e;
            }
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/tickets/upsert", this::upsert)
                .delete("/tickets/delete/{ticketId}", this::delete)
                .post("/tickets/search", this::search)
                .get("/tickets/all", this::all)
                .get("/tickets/logs", this::logs)
                .get("/tickets/config", this::config);
    }


    private void config(ServerRequest serverRequest, ServerResponse serverResponse) {
        serverResponse.header(HeaderNames.CONTENT_TYPE, "application/json")
                .send(Map.of("defaultZoom", defaultZoom));
    }

    private void logs(ServerRequest serverRequest, ServerResponse serverResponse) {
        var logs = logService.getLogs().stream()
                .map(l -> new LogsResponse.LogInfo(l.message(), l.type(), l.timestamp()))
                .toList();
        serverResponse.header(HeaderNames.CONTENT_TYPE, "application/json")
                .send(new LogsResponse(logs));

    }

    private void all(ServerRequest serverRequest, ServerResponse serverResponse) {
        var ticketInfos = vectorService.retrieveAllTickets();
        var inMemoryTickets = ticketStore.getAllTickets();

        Map<Long, TicketsResponse.TicketInfo> ticketMap = ticketInfos.stream()
                .collect(toMap(
                        TicketsResponse.TicketInfo::id,
                        Function.identity()
                ));

        inMemoryTickets.stream()
                .map(im -> new TicketsResponse.TicketInfo(im.ticketId(), im.ticketType(), im.text(), im.vector()))
                .forEach(ticket -> ticketMap.put(ticket.id(), ticket));

        var tickets = ticketMap.values().stream()
                .sorted((a, b) -> Long.compare(b.id(), a.id()))
                .toList();

        serverResponse.header(HeaderNames.CONTENT_TYPE, "application/json").send(new TicketsResponse(tickets));

    }

    private void delete(ServerRequest serverRequest, ServerResponse serverResponse) {
        try {
            String ticketId = serverRequest.path().pathParameters().get("ticketId");
            if (ticketId == null) {
                serverResponse.status(Status.BAD_REQUEST_400).send(Map.of("error", "id is required"));
                return;
            }
            logService.addLog("Delete request for ticket #" + ticketId, "delete");
            vectorService.deleteTicket(Long.valueOf(ticketId));
            ticketStore.removeTicket(Long.valueOf(ticketId));
            serverResponse.header(HeaderNames.CONTENT_TYPE, "application/json").send(new StatusResponse("OK"));
        } catch (Exception e) {
            serverResponse.status(Status.BAD_REQUEST_400).send(Map.of("error", e.getMessage()));
        }
    }

    private void search(ServerRequest serverRequest, ServerResponse serverResponse) {
        try {
            SearchRequest request = serverRequest.content().as(SearchRequest.class);
            if (request.text() == null || request.ticketId() == null) {
                serverResponse.status(Status.BAD_REQUEST_400).send(Map.of("error", "text and id are required"));
                return;
            }
            int maxResults = request.maxResults() != null ? request.maxResults() : 5;
            double minScore = request.minScore() != null ? request.minScore() : 0.0;
            logService.addLog("Similarity search for ticket #" + request.ticketId(), "search");
            var searchResults = vectorService.searchSimilar(request.text(), maxResults, minScore, request.ticketId());
            var relatedTicketIds = searchResults.stream().map(VectorService.SearchResult::ticketId).toList();
            String logMessage = "Returned " + searchResults.size() + " similar tickets";
            logService.addLog(logMessage, "search");
            serverResponse.header(HeaderNames.CONTENT_TYPE, "application/json").send(new SearchResponse(relatedTicketIds));
        } catch (Exception e) {
            serverResponse.status(Status.BAD_REQUEST_400).send(Map.of("error", e.getMessage()));
        }
    }

    private void upsert(ServerRequest serverRequest, ServerResponse serverResponse) {
        try {
            UpsertRequest request = serverRequest.content().as(UpsertRequest.class);
            if (request.ticketId() == null || request.text() == null || request.ticketType() == null) {
                serverResponse.status(Status.BAD_REQUEST_400).send(Map.of("error", "id, type, and text are required"));
                return;
            }
            logService.addLog("Received ticket #" + request.ticketId() + " via upsert endpoint", "upsert");
            float[] embedding = embeddingService.embed(request.text());
            vectorService.upsertTicket(request.ticketId(), request.ticketType(), request.text(), embedding);
            ticketStore.storeTicket(request.ticketId(), request.ticketType(), request.text(), embedding);
            logService.addLog("Stored embedding for ticket #" + request.ticketId(), "upsert");
            serverResponse.header(HeaderNames.CONTENT_TYPE, "application/json").send(new StatusResponse("OK"));
        } catch (Exception e) {
            logService.addLog("No longer connected to the API", e.toString());
            serverResponse.status(Status.BAD_REQUEST_400).send(Map.of("error", e.getMessage()));
        }
    }
}
