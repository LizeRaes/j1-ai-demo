package com.example.ticket.config;

import com.example.ticket.domain.model.Ticket;
import com.example.ticket.domain.model.Comment;
import com.example.ticket.dto.CreateIncomingRequestDto;
import com.example.ticket.dto.TicketDto;
import com.example.ticket.mapper.TicketMapper;
import com.example.ticket.persistence.CommentRepository;
import com.example.ticket.persistence.TicketRepository;
import com.example.ticket.service.adapter.CommentService;
import com.example.ticket.service.adapter.IncomingRequestService;
import com.example.ticket.service.adapter.TicketStateService;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

@ApplicationScoped
public class DataSeeder {

    private static final Logger LOGGER = Logger.getLogger(DataSeeder.class.getName());

    @ConfigProperty(name = "demo.dir.location")
    String directory;

    @Inject
    TicketStateService ticketStateService;

    @Inject
    CommentService commentService;

    @Inject
    IncomingRequestService incomingRequestService;

    @Transactional
    void onStart(@Observes StartupEvent ev) throws URISyntaxException, IOException {
        String demoDataMode = System.getProperty("DemoData");
        String emptyMode = System.getProperty("Empty");

        // Priority: DemoData > Empty > KeepData (default)
        if (demoDataMode != null && !demoDataMode.isEmpty()) {
            clearAllData();
            int totalTickets = loadDemoTicketsFromFiles();
            LOGGER.info("Demo data loaded: " + totalTickets + " tickets from demo-data/*.json files");
        } else if (emptyMode != null && !emptyMode.isEmpty()) {
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
        incomingRequestService.deleteAll();
        LOGGER.info("Cleared all tickets, comments, and incoming requests");
    }

    @Transactional
    int loadDemoTicketsFromFiles() throws URISyntaxException,IOException {
        // List of demo data files to load
        URL url = Thread.currentThread().getContextClassLoader().getResource(directory);
        Path dirPath = Paths.get(Objects.requireNonNull(url).toURI());


        int totalTickets = 0;
        try (Stream<Path> files = Files.walk(dirPath)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                try (InputStream docStream = Files.newInputStream(path)) {

                    int fileTicketCount = 0;
                    ObjectMapper mapper = new ObjectMapper();
                    List<TicketDto> tickets = mapper.readValue(docStream, new TypeReference<>() {
                    });

                    TicketMapper ticketMapper = new TicketMapper();
                    for (TicketDto dto : tickets) {
                        Ticket ticket = ticketMapper.toTicket(dto);

                        if (ticket.getId() == null ) {
                            ticket.setId(ticketStateService.findMaxId() + 1);
                        }
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
                    LOGGER.log(Level.SEVERE, "⚠ Error loading " + path + ": ", e);
                }
            }
        }

        return totalTickets;
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
