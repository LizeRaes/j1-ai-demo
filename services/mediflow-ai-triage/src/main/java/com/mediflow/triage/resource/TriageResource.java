package com.mediflow.triage.resource;

import com.mediflow.triage.dto.AiTriageResult;
import com.mediflow.triage.dto.DocumentSearchResponse;
import com.mediflow.triage.dto.PolicyCitation;
import com.mediflow.triage.dto.SimilaritySearchResponse;
import com.mediflow.triage.dto.TicketView;
import com.mediflow.triage.dto.TriageRequest;
import com.mediflow.triage.dto.TriageResponse;
import com.mediflow.triage.service.AiService;
import com.mediflow.triage.service.DocumentService;
import com.mediflow.triage.service.DocumentService.DocumentServiceException;
import com.mediflow.triage.service.EventLogService;
import com.mediflow.triage.service.SimilarityService;
import com.mediflow.triage.service.SimilarityService.SimilarityServiceException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/triage/v1")
public class TriageResource {

    private static final Logger LOG = Logger.getLogger(TriageResource.class);

    @Inject
    AiService aiService;

    @Inject
    SimilarityService similarityService;

    @Inject
    DocumentService documentService;

    @Inject
    EventLogService eventLogService;

    @ConfigProperty(name = "ai-triage.similarity.max-results", defaultValue = "5")
    Integer maxSimilarityResults;

    @ConfigProperty(name = "ai-triage.documents.max-results", defaultValue = "5")
    Integer maxDocumentResults;

    @ConfigProperty(name = "ai-triage.documents.min-score", defaultValue = "0.3")
    Double minDocumentScore;

    @POST
    @Path("/classify")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public TriageResponse classify(TriageRequest request) {
        String similarityServiceError = null;
        String documentServiceError = null;
        Integer ticketId = request.getTicketId();

        try {
            eventLogService.addEvent("INFO", "Received triage request for ticket " + ticketId, ticketId);
            
            // Validate request
            validateRequest(request);

            // Call AI service
            eventLogService.addEvent("INFO", "Calling AI service for ticket " + ticketId, ticketId);
            AiTriageResult aiResult = aiService.triage(request.getMessage(), request.getAllowedTicketTypes());

            // Validate and enforce constraints
            validateAiResult(aiResult, request.getAllowedTicketTypes());

            // Clamp values (already done in AiService, but double-check)
            int urgencyScore = Math.max(1, Math.min(10, aiResult.getUrgencyScore()));
            int confidence = Math.max(0, Math.min(100, aiResult.getAiConfidencePercent()));

            // Call similarity service to find related tickets
            // Capture any errors but don't fail the request
            List<Long> relatedTicketIds;
            try {
                eventLogService.addEvent("INFO", "Searching for similar tickets for ticket " + ticketId, ticketId);
                relatedTicketIds = findSimilarTickets(aiResult.getTicketType(), request.getMessage(), request.getTicketId());
                eventLogService.addEvent("INFO", "Found " + relatedTicketIds.size() + " similar tickets for ticket " + ticketId, ticketId);
            } catch (SimilarityServiceException e) {
                // Capture full error info (type and message)
                similarityServiceError = e.getFullErrorInfo();
                // Log as warning since this is handled gracefully
                LOG.warnf("Similarity service error (non-fatal): %s", similarityServiceError);
                eventLogService.addEvent("WARN", "Similarity service error for ticket " + ticketId + ": " + similarityServiceError, ticketId);
                // Continue with empty list
                relatedTicketIds = List.of();
            }

            // Call document service to find related policy/company documents
            // Capture any errors but don't fail the request
            List<PolicyCitation> policyCitations;
            try {
                eventLogService.addEvent("INFO", "Searching for policy documents for ticket " + ticketId, ticketId);
                policyCitations = findPolicyDocuments(request.getMessage());
                eventLogService.addEvent("INFO", "Found " + policyCitations.size() + " policy documents for ticket " + ticketId, ticketId);
            } catch (DocumentServiceException e) {
                // Capture full error info (type and message)
                documentServiceError = e.getFullErrorInfo();
                // Log as warning since this is handled gracefully
                LOG.warnf("Document service error (non-fatal): %s", documentServiceError);
                eventLogService.addEvent("WARN", "Document service error for ticket " + ticketId + ": " + documentServiceError, ticketId);
                // Continue with empty list
                policyCitations = List.of();
            }

            TriageResponse response = TriageResponse.success(aiResult.getTicketType(), urgencyScore, confidence);
            response.setRelatedTicketIds(relatedTicketIds);
            response.setPolicyCitations(policyCitations);
            
            // Store ticket view
            TicketView ticketView = createTicketView(request, response);
            eventLogService.addTicket(ticketView);
            eventLogService.addEvent("INFO", "Successfully processed ticket " + ticketId + " - Type: " + aiResult.getTicketType() + ", Urgency: " + urgencyScore, ticketId);
            
            return response;

        } catch (RuntimeException e) {
            LOG.errorf(e, "Error processing triage request");
            String failReason = extractFailReason(e);
            
            // If similarity service also had an error, concatenate it
            if (similarityServiceError != null) {
                failReason = failReason + " | similar ticket service error: - " + similarityServiceError;
            }
            // If document service also had an error, concatenate it
            if (documentServiceError != null) {
                failReason = failReason + " | document service error: - " + documentServiceError;
            }
            
            TriageResponse response = TriageResponse.failed(failReason);
            eventLogService.addEvent("ERROR", "Failed to process ticket " + ticketId + ": " + failReason, ticketId);
            
            // Store failed ticket view
            TicketView ticketView = createTicketView(request, response);
            eventLogService.addTicket(ticketView);
            
            return response;
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error processing triage request");
            String failReason = "AI_TRIAGE_FAILED: " + e.getClass().getSimpleName() + ", " + e.getMessage();
            
            // If similarity service also had an error, concatenate it
            if (similarityServiceError != null) {
                failReason = failReason + " | similar ticket service error: - " + similarityServiceError;
            }
            // If document service also had an error, concatenate it
            if (documentServiceError != null) {
                failReason = failReason + " | document service error: - " + documentServiceError;
            }
            
            TriageResponse response = TriageResponse.failed(failReason);
            eventLogService.addEvent("ERROR", "Unexpected error processing ticket " + ticketId + ": " + failReason, ticketId);
            
            // Store failed ticket view
            TicketView ticketView = createTicketView(request, response);
            eventLogService.addTicket(ticketView);
            
            return response;
        }
    }

    private TicketView createTicketView(TriageRequest request, TriageResponse response) {
        TicketView ticketView = new TicketView();
        ticketView.setTicketId(request.getTicketId());
        ticketView.setIncomingRequestId(request.getIncomingRequestId());
        ticketView.setMessage(request.getMessage());
        ticketView.setStatus(response.getStatus());
        ticketView.setTicketType(response.getTicketType());
        ticketView.setUrgencyScore(response.getUrgencyScore());
        ticketView.setAiConfidencePercent(response.getAiConfidencePercent());
        ticketView.setRelatedTicketIds(response.getRelatedTicketIds());
        ticketView.setPolicyCitations(response.getPolicyCitations());
        ticketView.setFailReason(response.getFailReason());
        return ticketView;
    }

    private void validateRequest(TriageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (request.getAllowedTicketTypes() == null || request.getAllowedTicketTypes().isEmpty()) {
            throw new IllegalArgumentException("Allowed ticket types cannot be empty");
        }
        if (request.getTicketId() == null) {
            throw new IllegalArgumentException("Ticket ID cannot be null");
        }
    }

    private void validateAiResult(AiTriageResult result, List<TriageRequest.TicketTypeInfo> allowedTypes) {
        if (result == null) {
            throw new RuntimeException("AI service returned null result");
        }
        if (result.getTicketType() == null) {
            throw new RuntimeException("AI service did not return a ticket type");
        }

        List<String> allowedTypeNames = allowedTypes.stream()
                .map(TriageRequest.TicketTypeInfo::getType)
                .collect(Collectors.toList());

        if (!allowedTypeNames.contains(result.getTicketType())) {
            throw new RuntimeException("CONTRACT_VIOLATION: AI returned ticket type '" + 
                    result.getTicketType() + "' which is not in the allowed list: " + allowedTypeNames);
        }

        if (result.getUrgencyScore() == null || result.getUrgencyScore() < 1 || result.getUrgencyScore() > 10) {
            throw new RuntimeException("CONTRACT_VIOLATION: Urgency score must be between 1 and 10, got: " + 
                    result.getUrgencyScore());
        }

        if (result.getAiConfidencePercent() == null || 
            result.getAiConfidencePercent() < 0 || result.getAiConfidencePercent() > 100) {
            throw new RuntimeException("CONTRACT_VIOLATION: Confidence must be between 0 and 100, got: " + 
                    result.getAiConfidencePercent());
        }
    }

    private String extractFailReason(Exception e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.startsWith("SERVICE_TIMEOUT:")) {
                return message;
            } else if (message.startsWith("PARSE_FAILED:")) {
                return message;
            } else if (message.startsWith("CONTRACT_VIOLATION:")) {
                return "AI_TRIAGE_FAILED: " + message;
            } else if (message.startsWith("AI_TRIAGE_FAILED:")) {
                return message;
            }
        }
        return "AI_TRIAGE_FAILED: " + e.getClass().getSimpleName() + ", " + message;
    }

    private List<Long> findSimilarTickets(String ticketType, String message, Integer ticketId) throws SimilarityServiceException {
        SimilaritySearchResponse similarityResponse = similarityService.searchSimilarTickets(
                ticketType, message, maxSimilarityResults, ticketId);
        return similarityResponse != null && similarityResponse.getRelatedTicketIds() != null
                ? similarityResponse.getRelatedTicketIds()
                : List.of();
    }

    private List<PolicyCitation> findPolicyDocuments(String message) throws DocumentServiceException {
        DocumentSearchResponse documentResponse = documentService.searchDocuments(
                message, maxDocumentResults, minDocumentScore);
        if (documentResponse != null && documentResponse.getResults() != null) {
            return documentResponse.getResults().stream()
                    .filter(result -> result != null) // Filter out null results
                    .map(PolicyCitation::new)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
