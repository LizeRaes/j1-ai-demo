package com.example.ticket.rest;

import java.util.Map;
import java.util.function.Function;

import io.helidon.common.Default;
import io.helidon.config.Configuration;
import io.helidon.http.BadRequestException;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

import com.example.ticket.dto.LogsResponse;
import com.example.ticket.dto.SearchRequest;
import com.example.ticket.dto.SearchResponse;
import com.example.ticket.dto.StatusResponse;
import com.example.ticket.dto.TicketsResponse;
import com.example.ticket.dto.UpsertRequest;
import com.example.ticket.service.DemoDataService;
import com.example.ticket.service.EmbeddingService;
import com.example.ticket.service.LogService;
import com.example.ticket.service.TicketStore;
import com.example.ticket.service.VectorService;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON_VALUE;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("deprecation")
@RestServer.Endpoint
@Http.Path("/api/similarity/tickets")
@Service.Singleton
@Service.RunLevel(Service.RunLevel.STARTUP)
public class SimilarityEndpoint {

    private static final System.Logger log = System.getLogger(LogService.LOGGER_NAME);

    private final int defaultZoom;
    private final boolean useDemoData;
    private final DemoDataService demoDataService;

    private final TicketStore ticketStore;
    private final EmbeddingService embeddingService;
    private final VectorService vectorService;
    private final LogService logHandler;

    @Service.Inject
    SimilarityEndpoint(@Default.Int(100) @Configuration.Value("ui.font.zoom.default") int defaultZoom,
                       @Default.Boolean(false) @Configuration.Value("DemoData") boolean useDemoData,
                       DemoDataService demoDataService,
                       EmbeddingService embeddingService,
                       TicketStore ticketStore,
                       VectorService vectorService,
                       LogService logHandler) {
        this.defaultZoom = defaultZoom;
        this.useDemoData = useDemoData;
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
            try {
                demoDataService.loadDemoDataAsync();
                log.log(INFO, "Demo data loaded successfully");
            } catch (Exception e) {
                log.log(ERROR, "Failed to load demo data: " + e.getMessage());
                throw e;
            }
        }
    }

    @Http.GET
    @Http.Path("/config")
    @Http.Produces(APPLICATION_JSON_VALUE)
    Map<String, Object> config() {
        return Map.of("defaultZoom", defaultZoom);
    }

    @Http.GET
    @Http.Path("/logs")
    @Http.Produces(APPLICATION_JSON_VALUE)
    LogsResponse logs() {
        var logs = logHandler.getLogs().stream()
                .map(l -> new LogsResponse.LogInfo(l.message(), l.type(), l.timestamp()))
                .toList();
        return new LogsResponse(logs);
    }

    @Http.GET
    @Http.Path("/all")
    @Http.Produces(APPLICATION_JSON_VALUE)
    TicketsResponse all() {
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

        return new TicketsResponse(tickets);
    }

    @Http.DELETE
    @Http.Path("/delete/{ticketId}")
    @Http.Produces(APPLICATION_JSON_VALUE)
    StatusResponse delete(@Http.PathParam("ticketId") String ticketId) {
        if (ticketId == null) {
            throw new BadRequestException("id is required");
        }
        try {
            log.log(INFO, "Delete request for ticket #" + ticketId);
            vectorService.deleteTicket(Long.valueOf(ticketId));
            ticketStore.removeTicket(Long.valueOf(ticketId));
            return new StatusResponse("OK");
        } catch (Exception e) {
            throw new BadRequestException("Search error", e);
        }
    }

    @Http.POST
    @Http.Path("/search")
    @Http.Produces(APPLICATION_JSON_VALUE)
    SearchResponse search(@Http.Entity SearchRequest request) {
        if (request.text() == null || request.ticketId() == null) {
            throw new BadRequestException("text and id are required");
        }
        try {
            int maxResults = request.maxResults() != null ? request.maxResults() : 5;
            double minScore = request.minScore() != null ? request.minScore() : 0.0;
            log.log(INFO, "Similarity search for ticket #" + request.ticketId());
            var searchResults = vectorService.searchSimilar(request.text(), maxResults, minScore, request.ticketId());
            var relatedTicketIds = searchResults.stream().map(VectorService.SearchResult::ticketId).toList();
            String logMessage = "Returned " + searchResults.size() + " similar tickets";
            log.log(INFO, logMessage);
            return new SearchResponse(relatedTicketIds);
        } catch (Exception e) {
            throw new BadRequestException("Search error", e);
        }
    }

    @Http.POST
    @Http.Path("/upsert")
    @Http.Produces(APPLICATION_JSON_VALUE)
    StatusResponse upsert(@Http.Entity UpsertRequest request) {
        if (request.ticketId() == null || request.text() == null || request.ticketType() == null) {
            throw new BadRequestException("id, type, and text are required");
        }
        try {
            log.log(INFO, "Received ticket #" + request.ticketId() + " via upsert endpoint");
            float[] embedding = embeddingService.embed(request.text());
            vectorService.upsertTicket(request.ticketId(), request.ticketType(), request.text(), embedding);
            ticketStore.storeTicket(request.ticketId(), request.ticketType(), request.text(), embedding);
            log.log(INFO, "Stored embedding for ticket #" + request.ticketId());
            return new StatusResponse("OK");
        } catch (Exception e) {
            log.log(ERROR, "No longer connected to the API", e);
            throw new BadRequestException("No longer connected to the API", e);
        }
    }
}
