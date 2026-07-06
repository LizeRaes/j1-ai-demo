package com.example.ticket.rest;

import java.util.Map;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.http.BadRequestException;
import io.helidon.service.registry.Service;

import com.example.ticket.dto.LogsResponse;
import com.example.ticket.dto.MappedStatusResponse;
import com.example.ticket.dto.MappedUpsertRequest;
import com.example.ticket.dto.SearchRequest;
import com.example.ticket.dto.SearchResponse;
import com.example.ticket.dto.StatusResponse;
import com.example.ticket.dto.TicketsResponse;
import com.example.ticket.model.TicketData;
import com.example.ticket.service.DemoDataService;
import com.example.ticket.service.EmbeddingService;
import com.example.ticket.service.LogService;
import com.example.ticket.service.TicketStore;
import com.example.ticket.service.VectorService;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.util.stream.Collectors.toMap;


@Service.Singleton
@Service.RunLevel(Service.RunLevel.STARTUP)
public class SimilarityEndpoint {

    private static final System.Logger log = System.getLogger(LogService.LOGGER_NAME);

    private final int defaultZoom;
    private final boolean useDemoData;
    private final DemoDataService demoDataService;
    private final SimilaritySearchDefaults searchDefaults;

    private final TicketStore ticketStore;
    private final EmbeddingService embeddingService;
    private final VectorService vectorService;
    private final LogService logHandler;

    @Service.Inject
    SimilarityEndpoint(Config config,
                       DemoDataService demoDataService,
                       EmbeddingService embeddingService,
                       TicketStore ticketStore,
                       VectorService vectorService,
                       LogService logHandler) {
        this.defaultZoom = config.get("ui.font.zoom.default").asInt().orElse(100);
        this.useDemoData = config.get("DemoData").asBoolean().orElse(false);
        this.searchDefaults = searchDefaults(config);
        this.demoDataService = demoDataService;
        this.ticketStore = ticketStore;
        this.embeddingService = embeddingService;
        this.vectorService = vectorService;
        this.logHandler = logHandler;
    }

    @Service.PostConstruct
    public void beforeStart() {
        if (useDemoData) {
            log.log(INFO, "DemoData flag detected. Loading demo data and wiping Oracle DB...");
            demoDataService.loadDemoDataAsync()
                    .whenComplete((_, failure) -> {
                        if (failure == null) {
                            log.log(INFO, "Demo data loaded successfully");
                        } else {
                            log.log(ERROR, "Failed to load demo data", failure);
                        }
                    });
        }
    }

    private static SimilaritySearchDefaults searchDefaults(Config config) {
        return new SimilaritySearchDefaults(
                config.get("similarity.tickets.search.max-results").asInt().orElse(5),
                config.get("similarity.tickets.search.min-score").asDouble().orElse(0.0));
    }

    Map<String, Object> config() {
        return Map.of("defaultZoom", defaultZoom);
    }

    LogsResponse logs() {
        var logs = logHandler.getLogs().stream()
                .map(l -> new LogsResponse.LogInfo(l.message(), l.type(), l.timestamp()))
                .toList();
        return new LogsResponse(logs);
    }

    TicketsResponse all() {
        var ticketInfos = vectorService.retrieveAllTickets();
        var inMemoryTickets = ticketStore.getAllTickets();

        Map<Long, TicketData> ticketMap = ticketInfos.stream()
                .collect(toMap(
                        TicketData::ticketId,
                        Function.identity()
                ));

        inMemoryTickets.forEach(ticket -> ticketMap.put(ticket.ticketId(), ticket));

        return SimilarityMapper.toTicketsResponse(ticketMap.values()).asTicketsResponse();
    }

    StatusResponse delete(String ticketId) {
        if (ticketId == null) {
            throw new BadRequestException("id is required");
        }
        try {
            log.log(INFO, "Delete request for ticket #" + ticketId);
            vectorService.deleteTicket(Long.valueOf(ticketId));
            ticketStore.removeTicket(Long.valueOf(ticketId));
            return SimilarityMapper.toStatusResponse("OK").asStatusResponse();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Search error", e);
        }
    }

    SearchResponse search(SearchRequest request) {
        try {
            var query = SimilarityMapper.toSearchQuery(request, searchDefaults).asTicketSearchQuery();
            log.log(INFO, "Similarity search for ticket #" + query.excludeTicketId());
            var searchResults = vectorService.searchSimilar(query);
            String logMessage = "Returned " + searchResults.size() + " similar tickets";
            log.log(INFO, logMessage);
            return SimilarityMapper.toSearchResponse(searchResults).asSearchResponse();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Search error", e);
        }
    }

    MappedStatusResponse upsert(MappedUpsertRequest request) {
        try {
            var upsertRequest = SimilarityMapper.validateUpsertRequest(request).asUpsertRequest();
            log.log(INFO, "Received ticket #" + upsertRequest.ticketId() + " via upsert endpoint");
            float[] embedding = embeddingService.embed(upsertRequest.text());
            var ticket = SimilarityMapper.toTicketData(upsertRequest, embedding).asTicketData();
            vectorService.upsertTicket(ticket);
            ticketStore.storeTicket(ticket);
            log.log(INFO, "Stored embedding for ticket #" + ticket.ticketId());
            return SimilarityMapper.toStatusResponse("OK");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.log(ERROR, "No longer connected to the API", e);
            throw new BadRequestException("No longer connected to the API", e);
        }
    }

}
