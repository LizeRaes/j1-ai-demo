package com.example.ticket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.domain.constants.TicketSource;
import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.constants.TicketType;
import com.example.ticket.domain.model.IncomingRequest;
import com.example.ticket.domain.model.Ticket;
import com.example.ticket.domain.model.TicketComment;
import com.example.ticket.persistence.CommentRepository;
import com.example.ticket.persistence.TicketRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class DataSeeder {

    private static final Logger LOGGER = Logger.getLogger(DataSeeder.class.getName());

    @Inject
    TicketRepository ticketRepository;

    @Inject
    CommentRepository commentRepository;

    TicketTypeTeamMapper ticketTypeTeamMapper;

    @Inject
    ActorContext context;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager entityManager;


    @Transactional
    void onStart(@Observes StartupEvent ev) {
        String demoDataMode = System.getProperty("DemoData");
        String emptyMode = System.getProperty("Empty");
        ticketTypeTeamMapper = new TicketTypeTeamMapper();

        // Priority: DemoData > Empty > KeepData (default)
        if (demoDataMode != null && !demoDataMode.isEmpty()) {
            // Load demo data: clear database and load from all JSON files in demo-data/
            clearAllData();
            int totalTickets = loadDemoTicketsFromFiles();
            LOGGER.info("Demo data loaded: " + totalTickets + " tickets from demo-data/*.json files");
        } else if (emptyMode != null && !emptyMode.isEmpty()) {
            // Start with empty database
            clearAllData();
            LOGGER.info("Database cleared (Empty mode)");
        } else {
            // KeepData mode (default): only seed incoming requests if database is empty
            if (IncomingRequest.count() == 0) {
                seedDefaultIncomingRequests();
                LOGGER.info("✓ Default incoming requests seeded");
            }
        }
    }

    private void clearAllData() {
        // Delete in order to respect foreign key constraints
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        IncomingRequest.deleteAll();
        LOGGER.info("Cleared all tickets, comments, and incoming requests");
    }

    private int loadDemoTicketsFromFiles() {
        // List of demo data files to load
        String[] demoFiles = {
            "demo-data/demo-tickets-access-other.json",
            "demo-data/demo-tickets-billing.json",
            "demo-data/demo-tickets-engineering.json",
            "demo-data/demo-tickets-scheduling.json"
        };

        int totalTickets = 0;
        int filesLoaded = 0;

        for (String filePath : demoFiles) {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream(filePath);
                if (is == null) {
                    System.out.println("  → Skipping " + filePath + " (not found)");
                    continue;
                }

                List<Map<String, Object>> tickets = objectMapper.readValue(is, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

                int fileTicketCount = 0;
                int fileErrorCount = 0;
                for (Map<String, Object> ticketData : tickets) {
                    try {
                        // Check if ticket ID is specified in JSON
                        Long ticketId = null;
                        if (ticketData.get("id") != null) {
                            ticketId = ((Number) ticketData.get("id")).longValue();
                            // Delete existing ticket with this ID if it exists (to override)
                            Ticket existing = ticketRepository.findById(ticketId);
                            if (existing != null) {
                                // Delete comments first (foreign key constraint)
                                commentRepository.findByTicketId(ticketId).forEach(commentRepository::delete);
                                ticketRepository.delete(existing);
                            }
                        }
                        
                        Ticket ticket = createTicketFromMap(ticketData);
                        // Set explicit ID if provided - use native SQL INSERT to bypass IDENTITY generation
                        if (ticketId != null) {
                            ticket.id = ticketId;
                            
                            // Use native SQL INSERT to set explicit ID (bypasses IDENTITY generation)
                            // Column order matches actual database schema
                            String sql = "INSERT INTO tickets (id, user_id, original_request, ticket_type, status, source, " +
                                "assigned_team, assigned_to, urgency_flag, urgency_score, ai_confidence, rollback_allowed, " +
                                "ai_payload_json, incoming_request_id) " +
                                "VALUES (:id, :userId, :originalRequest, :ticketType, :status, :source, " +
                                ":assignedTeam, :assignedTo, :urgencyFlag, :urgencyScore, :aiConfidence, :rollbackAllowed, " +
                                ":aiPayloadJson, :incomingRequestId)";
                            
                            entityManager.createNativeQuery(sql)
                                .setParameter("id", ticketId)
                                .setParameter("userId", ticket.getUserId())
                                .setParameter("originalRequest", ticket.getOriginalRequest())
                                .setParameter("ticketType", ticket.getTicketType().name())
                                .setParameter("status", ticket.getStatus().name())
                                .setParameter("source", ticket.getSource().name())
                                .setParameter("assignedTeam", ticket.getAssignedTeam())
                                .setParameter("assignedTo", ticket.getAssignedTo())
                                .setParameter("urgencyFlag", ticket.getUrgencyFlag())
                                .setParameter("urgencyScore", ticket.getUrgencyScore())
                                .setParameter("aiConfidence", ticket.getAiConfidence())
                                .setParameter("rollbackAllowed", ticket.getRollbackAllowed())
                                .setParameter("aiPayloadJson", ticket.getAiPayloadJson())
                                .setParameter("incomingRequestId", ticket.getIncomingRequestId())
                                .executeUpdate();
                        } else {
                            ticketRepository.persist(ticket);
                        }

                        // Create comments if any
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> comments = (List<Map<String, Object>>) ticketData.get("comments");
                        if (comments != null) {
                            for (Map<String, Object> commentData : comments) {
                                TicketComment comment = new TicketComment();
                                comment.setTicketId(ticket.id);
                                comment.setAuthorId((String) commentData.get("authorId"));
                                comment.setBody((String) commentData.get("body"));
                                commentRepository.persist(comment);
                            }
                        }
                        fileTicketCount++;
                    } catch (Exception e) {
                        fileErrorCount++;
                        System.err.println("  ⚠ Skipping ticket in " + filePath + ": " + e.getMessage());
                    }
                }

                totalTickets += fileTicketCount;
                if (fileTicketCount > 0) {
                    filesLoaded++;
                    if (fileErrorCount > 0) {
                        System.out.println("  → Loaded " + fileTicketCount + " tickets from " + filePath + " (" + fileErrorCount + " errors)");
                    } else {
                        System.out.println("  → Loaded " + fileTicketCount + " tickets from " + filePath);
                    }
                } else if (fileErrorCount > 0) {
                    System.err.println("  ⚠ Failed to load any tickets from " + filePath + " (" + fileErrorCount + " errors)");
                }
                is.close();
            } catch (Exception e) {
               LOGGER.log(Level.SEVERE, "⚠ Error loading " + filePath + ": ", e);
            }
        }

        if (filesLoaded == 0) {
           LOGGER.warning("⚠ Warning: No demo data files found in resources/demo-data/");
        }

        return totalTickets;
    }

    private Ticket createTicketFromMap(Map<String, Object> data) {
        Ticket ticket = new Ticket();
        ticket.setUserId((String) data.get("userId"));
        ticket.setOriginalRequest((String) data.get("originalRequest"));
        
        // Parse ticketType
        String ticketTypeStr = (String) data.get("ticketType");
        ticket.setTicketType(TicketType.valueOf(ticketTypeStr));
        
        // Parse status (default to FROM_DISPATCH if not specified)
        // Map common status names to valid enum values
        String statusStr = (String) data.get("status");
        ticket.setStatus(mapStatusString(statusStr));
        
        // Parse source (default to MANUAL if not specified)
        String sourceStr = (String) data.get("source");
        ticket.setSource(sourceStr != null ? TicketSource.valueOf(sourceStr) : TicketSource.MANUAL);
        
        // Assign team based on ticket type
        ticket.setAssignedTeam(ticketTypeTeamMapper.deriveTeamFromTicketType(ticket.getTicketType()).name());
        
        // Assign to user (use provided or default for team)
        String assignedTo = (String) data.get("assignedTo");
        ticket.setAssignedTo(assignedTo != null ? assignedTo : context.getDefaultUserIdForTeam(ticket.getAssignedTeam()));
        
        // Urgency fields
        ticket.setUrgencyFlag(data.get("urgencyFlag") != null ? (Boolean) data.get("urgencyFlag") : false);
        if (data.get("urgencyScore") != null) {
            ticket.setUrgencyScore(((Number) data.get("urgencyScore")).doubleValue());
        }
        
        // AI fields
        if (data.get("aiConfidence") != null) {
            ticket.setAiConfidence(((Number) data.get("aiConfidence")).doubleValue());
        }
        ticket.setAiPayloadJson((String) data.get("aiPayloadJson"));
        ticket.setRollbackAllowed(ticket.getSource().equals(TicketSource.AI) && ticket.getStatus().equals(TicketStatus.FROM_AI));
        
        return ticket;
    }

    private void seedDefaultIncomingRequests() {
        createRequest("u-alex",
                "I tried to cancel my appointment but it says it's too late, even though it's still 36 hours away.");
        createRequest("u-samira",
                "I was charged for an appointment that the doctor cancelled. Can I get a refund?");
        createRequest("u-jonas",
                "The reschedule button is disabled on my appointment and I can't change the time.");
        createRequest("u-lea",
                "I get an error when uploading my insurance card: 'Upload failed (E102)'.");
        createRequest("u-pascal",
                "Urgent: I need to cancel today's appointment in 2 hours. I'm sick.");
    }

    private TicketStatus mapStatusString(String statusStr) {
        if (statusStr == null || statusStr.isEmpty()) {
            return TicketStatus.FROM_DISPATCH;
        }

        String upper = statusStr.toUpperCase();
        return switch (upper) {
            case "OPEN", "NEW", "PENDING" -> TicketStatus.FROM_DISPATCH;
            case "RESOLVED", "CLOSED" -> TicketStatus.COMPLETED;
            case "TRIAGED", "IN_PROGRESS", "WAITING_ON_USER", "COMPLETED", "RETURNED_TO_DISPATCH", "ROLLED_BACK" -> TicketStatus.valueOf(upper);
            case String _ -> TicketStatus.FROM_DISPATCH;
        };
    }

    private void createRequest(String userId, String rawText) {
        IncomingRequest request = new IncomingRequest();
        request.setUserId(userId);
        request.setChannel("web");
        request.setRawText(rawText);
        request.setStatus(RequestStatus.NEW);
        request.persist();
    }
}
