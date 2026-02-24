package com.example.ticket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ticket.domain.enums.RequestStatus;
import com.example.ticket.domain.enums.TicketSource;
import com.example.ticket.domain.enums.TicketStatus;
import com.example.ticket.domain.enums.TicketType;
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
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DataSeeder {

    @Inject
    TicketRepository ticketRepository;

    @Inject
    CommentRepository commentRepository;

    @Inject
    TicketTypeTeamMapper ticketTypeTeamMapper;

    @Inject
    TeamUserMapper teamUserMapper;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager entityManager;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        String demoDataMode = System.getProperty("DemoData");
        String emptyMode = System.getProperty("Empty");

        // Priority: DemoData > Empty > KeepData (default)
        if (demoDataMode != null && !demoDataMode.isEmpty()) {
            // Load demo data: clear database and load from all JSON files in demo-data/
            clearAllData();
            int totalTickets = loadDemoTicketsFromFiles();
            System.out.println("✓ Demo data loaded: " + totalTickets + " tickets from demo-data/*.json files");
        } else if (emptyMode != null && !emptyMode.isEmpty()) {
            // Start with empty database
            clearAllData();
            System.out.println("✓ Database cleared (Empty mode)");
        } else {
            // KeepData mode (default): only seed incoming requests if database is empty
            if (IncomingRequest.count() == 0) {
                seedDefaultIncomingRequests();
                System.out.println("✓ Default incoming requests seeded");
            }
        }
    }

    private void clearAllData() {
        // Delete in order to respect foreign key constraints
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        IncomingRequest.deleteAll();
        System.out.println("  → Cleared all tickets, comments, and incoming requests");
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
                            // Manually set timestamps since @PrePersist doesn't run with native SQL
                            OffsetDateTime now = OffsetDateTime.now();
                            ticket.createdAt = now;
                            ticket.updatedAt = now;
                            
                            // Convert OffsetDateTime to Timestamp for MySQL datetime(6) compatibility
                            Timestamp createdAtTs = Timestamp.from(now.toInstant());
                            Timestamp updatedAtTs = Timestamp.from(now.toInstant());
                            
                            // Use native SQL INSERT to set explicit ID (bypasses IDENTITY generation)
                            // Column order matches actual database schema
                            String sql = "INSERT INTO tickets (id, user_id, original_request, ticket_type, status, source, " +
                                "assigned_team, assigned_to, urgency_flag, urgency_score, ai_confidence, rollback_allowed, " +
                                "ai_payload_json, incoming_request_id, created_at, updated_at) " +
                                "VALUES (:id, :userId, :originalRequest, :ticketType, :status, :source, " +
                                ":assignedTeam, :assignedTo, :urgencyFlag, :urgencyScore, :aiConfidence, :rollbackAllowed, " +
                                ":aiPayloadJson, :incomingRequestId, :createdAt, :updatedAt)";
                            
                            entityManager.createNativeQuery(sql)
                                .setParameter("id", ticketId)
                                .setParameter("userId", ticket.userId)
                                .setParameter("originalRequest", ticket.originalRequest)
                                .setParameter("ticketType", ticket.ticketType.name())
                                .setParameter("status", ticket.status.name())
                                .setParameter("source", ticket.source.name())
                                .setParameter("assignedTeam", ticket.assignedTeam)
                                .setParameter("assignedTo", ticket.assignedTo)
                                .setParameter("urgencyFlag", ticket.urgencyFlag)
                                .setParameter("urgencyScore", ticket.urgencyScore)
                                .setParameter("aiConfidence", ticket.aiConfidence)
                                .setParameter("rollbackAllowed", ticket.rollbackAllowed)
                                .setParameter("aiPayloadJson", ticket.aiPayloadJson)
                                .setParameter("incomingRequestId", ticket.incomingRequestId)
                                .setParameter("createdAt", createdAtTs)
                                .setParameter("updatedAt", updatedAtTs)
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
                System.err.println("⚠ Error loading " + filePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (filesLoaded == 0) {
            System.err.println("⚠ Warning: No demo data files found in resources/demo-data/");
        }

        return totalTickets;
    }

    private Ticket createTicketFromMap(Map<String, Object> data) {
        Ticket ticket = new Ticket();
        ticket.userId = (String) data.get("userId");
        ticket.originalRequest = (String) data.get("originalRequest");
        
        // Parse ticketType
        String ticketTypeStr = (String) data.get("ticketType");
        ticket.ticketType = TicketType.valueOf(ticketTypeStr);
        
        // Parse status (default to FROM_DISPATCH if not specified)
        // Map common status names to valid enum values
        String statusStr = (String) data.get("status");
        ticket.status = mapStatusString(statusStr);
        
        // Parse source (default to MANUAL if not specified)
        String sourceStr = (String) data.get("source");
        ticket.source = sourceStr != null ? TicketSource.valueOf(sourceStr) : TicketSource.MANUAL;
        
        // Assign team based on ticket type
        ticket.assignedTeam = ticketTypeTeamMapper.deriveTeamFromTicketType(ticket.ticketType).name();
        
        // Assign to user (use provided or default for team)
        String assignedTo = (String) data.get("assignedTo");
        ticket.assignedTo = assignedTo != null ? assignedTo : teamUserMapper.getDefaultUserIdForTeam(ticket.assignedTeam);
        
        // Urgency fields
        ticket.urgencyFlag = data.get("urgencyFlag") != null ? (Boolean) data.get("urgencyFlag") : false;
        if (data.get("urgencyScore") != null) {
            ticket.urgencyScore = ((Number) data.get("urgencyScore")).doubleValue();
        }
        
        // AI fields
        if (data.get("aiConfidence") != null) {
            ticket.aiConfidence = ((Number) data.get("aiConfidence")).doubleValue();
        }
        ticket.aiPayloadJson = (String) data.get("aiPayloadJson");
        ticket.rollbackAllowed = ticket.source == TicketSource.AI && ticket.status == TicketStatus.FROM_AI;
        
        return ticket;
    }

    private void seedDefaultIncomingRequests() {
        createRequest("u-alex", "web", 
            "I tried to cancel my appointment but it says it's too late, even though it's still 36 hours away.");
        createRequest("u-samira", "web", 
            "I was charged for an appointment that the doctor cancelled. Can I get a refund?");
        createRequest("u-jonas", "web", 
            "The reschedule button is disabled on my appointment and I can't change the time.");
        createRequest("u-lea", "web", 
            "I get an error when uploading my insurance card: 'Upload failed (E102)'.");
        createRequest("u-pascal", "web", 
            "Urgent: I need to cancel today's appointment in 2 hours. I'm sick.");
    }

    private TicketStatus mapStatusString(String statusStr) {
        if (statusStr == null || statusStr.isEmpty()) {
            return TicketStatus.FROM_DISPATCH;
        }
        
        // Map common status names to valid enum values
        String upper = statusStr.toUpperCase();
        switch (upper) {
            case "OPEN":
                // OPEN typically means a new ticket that needs triage
                return TicketStatus.FROM_DISPATCH;
            case "NEW":
                return TicketStatus.FROM_DISPATCH;
            case "PENDING":
                return TicketStatus.FROM_DISPATCH;
            case "RESOLVED":
            case "CLOSED":
                return TicketStatus.COMPLETED;
            default:
                // Try to parse as-is, throw exception if invalid
                try {
                    return TicketStatus.valueOf(upper);
                } catch (IllegalArgumentException e) {
                    System.err.println("  ⚠ Invalid status '" + statusStr + "', defaulting to FROM_DISPATCH");
                    return TicketStatus.FROM_DISPATCH;
                }
        }
    }

    private void createRequest(String userId, String channel, String rawText) {
        IncomingRequest request = new IncomingRequest();
        request.setUserId(userId);
        request.setChannel(channel);
        request.setRawText(rawText);
        request.setStatus(RequestStatus.NEW);
        request.persist();
    }
}
