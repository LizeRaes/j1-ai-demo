package com.example.appointment.config;

import com.example.appointment.domain.model.Ticket;
import com.example.appointment.domain.model.Comment;
import com.example.appointment.dto.CreateIncomingRequestDto;
import com.example.appointment.dto.TicketDto;
import com.example.appointment.mapper.TicketMapper;
import com.example.appointment.service.adapter.CommentService;
import com.example.appointment.service.adapter.IncomingRequestService;
import com.example.appointment.service.adapter.TicketStateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DataSeeder {

    private static final Logger LOGGER = Logger.getLogger(DataSeeder.class.getName());
    private static final String[] DEMO_DATA_FILES = {
            "demo-tickets-access-other.json",
            "demo-tickets-billing.json",
            "demo-tickets-engineering.json",
            "demo-tickets-scheduling.json"
    };

    @ConfigProperty(name = "demo.dir.location")
    String directory;

    @ConfigProperty(name = "demo.data.enabled")
    boolean demoDataEnabled;

    @ConfigProperty(name = "demo.data.empty")
    boolean demoDataEmpty;

    @Inject
    TicketStateService ticketStateService;

    @Inject
    CommentService commentService;

    @Inject
    IncomingRequestService incomingRequestService;


    @Transactional
    void onStart(@Observes StartupEvent ev) {
        // Priority: DemoData > Empty > KeepData (default)
        if (demoDataEnabled) {
            clearAllData();
            int totalTickets = loadDemoTicketsFromFiles();
            LOGGER.info("Demo data loaded: " + totalTickets + " tickets from demo-data/*.json files");
        } else if (demoDataEmpty) {
            clearAllData();
            LOGGER.info("Database cleared (Empty mode)");
        } else {
            // KeepData mode (default): only seed incoming requests if database is empty
            if (incomingRequestService.count() == 0) {
                seedDefaultIncomingRequests();
                LOGGER.info(" Default incoming requests seeded");
            }
        }
    }

    private void clearAllData() {
        commentService.deleteAll();
        ticketStateService.deleteAll();
        ticketStateService.resetAutoIncrement();
        incomingRequestService.deleteAll();
        LOGGER.info("Cleared all tickets, comments, and incoming requests");
    }

    @Transactional
    int loadDemoTicketsFromFiles() {
        String normalizedDirectory = normalizeDirectory(directory);

        int totalTickets = 0;
        ObjectMapper mapper = new ObjectMapper();
        TicketMapper ticketMapper = new TicketMapper();

        for (String fileName : DEMO_DATA_FILES) {
            String resourcePath = normalizedDirectory + "/" + fileName;

            try (InputStream docStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
                if (docStream == null) {
                    LOGGER.warning("Demo data resource missing on classpath: " + resourcePath);
                    continue;
                }

                int fileTicketCount = 0;
                List<TicketDto> tickets = mapper.readValue(docStream, new TypeReference<>() {
                });

                for (TicketDto dto : tickets) {
                    Ticket ticket = ticketMapper.toTicket(dto);
                    ticket.setId(null);
                    ticketStateService.persist(ticket);

                    if (dto.comments() != null) {
                        dto.comments().stream()
                                .map(cd -> {
                                    Comment c = new Comment();
                                    c.setTicket(ticket);
                                    c.setAuthorId(cd.authorId());
                                    c.setBody(cd.body());
                                    return c;
                                })
                                .forEach(commentService::persist);
                    }

                    fileTicketCount++;
                }

                totalTickets += fileTicketCount;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading demo data resource " + resourcePath, e);
            }
        }

        return totalTickets;
    }

    private String normalizeDirectory(String rawDirectory) {
        if (rawDirectory == null || rawDirectory.isBlank()) {
            return "demo-data";
        }

        String normalized = rawDirectory.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void seedDefaultIncomingRequests() {
        incomingRequestService.createIncomingRequest(new CreateIncomingRequestDto("u-alex",
                "web", "I tried to cancel my appointment but it says it's too late, even though it's still 36 hours away."));
        incomingRequestService.createIncomingRequest(new CreateIncomingRequestDto("u-samira",
                "web", "I was charged for an appointment that the doctor cancelled. Can I get a refund?"));
        incomingRequestService.createIncomingRequest(new CreateIncomingRequestDto("u-jonas",
                "web", "The reschedule button is disabled on my appointment and I can't change the time."));
        incomingRequestService.createIncomingRequest(new CreateIncomingRequestDto("u-lea",
                "web", "I get an error when uploading my insurance card: 'Upload failed (E102)'."));
        incomingRequestService.createIncomingRequest(new CreateIncomingRequestDto("u-pascal",
                "web", "Urgent: I need to cancel today's appointment in 2 hours. I'm sick."));
    }



}
