package com.medicalappointment.ticketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicalappointment.ticketing.dto.TriageRequestDto;
import com.medicalappointment.ticketing.dto.TriageResponseDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Client for calling external AI triage service.
 * All calls are async and non-blocking.
 */
@ApplicationScoped
public class TriageClient {
    
    @ConfigProperty(name = "ai-triage.url", defaultValue = "http://localhost:8081")
    String aiTriageUrl;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient;
    private final Executor executor;
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    
    public TriageClient() {
        // Use a dedicated executor for triage calls to avoid blocking request threads
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "triage-client-worker");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Get the executor for running async tasks that need transaction support.
     */
    public Executor getExecutor() {
        return executor;
    }
    
    /**
     * Call AI triage service asynchronously.
     * 
     * @param request The triage request
     * @return CompletableFuture that completes with the response or fails with an exception
     */
    public CompletableFuture<TriageResponseDto> classifyAsync(TriageRequestDto request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = objectMapper.writeValueAsString(request);
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(aiTriageUrl + "/api/triage/v1/classify"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(TIMEOUT)
                    .build();
                
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(), TriageResponseDto.class);
                } else {
                    // Non-200 status code - treat as failure
                    TriageResponseDto errorResponse = new TriageResponseDto();
                    errorResponse.status = "FAILED";
                    errorResponse.failReason = "AI triage service returned status " + response.statusCode();
                    return errorResponse;
                }
            } catch (java.net.http.HttpTimeoutException e) {
                // Timeout - return failure response
                TriageResponseDto errorResponse = new TriageResponseDto();
                errorResponse.status = "FAILED";
                errorResponse.failReason = "AI triage service timeout after 3 minutes";
                return errorResponse;
            } catch (java.net.ConnectException | java.net.UnknownHostException e) {
                // Connection refused or host unknown - service is down
                TriageResponseDto errorResponse = new TriageResponseDto();
                errorResponse.status = "FAILED";
                errorResponse.failReason = "AI triage service unavailable: " + e.getMessage();
                return errorResponse;
            } catch (Exception e) {
                // Any other exception - return failure response
                TriageResponseDto errorResponse = new TriageResponseDto();
                errorResponse.status = "FAILED";
                errorResponse.failReason = "AI triage service error: " + e.getMessage();
                return errorResponse;
            }
        }, executor);
    }
    
    /**
     * Build the list of allowed ticket types with descriptions.
     * This is fixed and always the same.
     */
    public static List<TriageRequestDto.AllowedTicketType> buildAllowedTicketTypes() {
        List<TriageRequestDto.AllowedTicketType> types = new ArrayList<>();
        
        types.add(createType("BILLING_REFUND", "User is asking for a refund or billing reimbursement"));
        types.add(createType("BILLING_OTHER", "Other billing-related issue requiring human review"));
        types.add(createType("SCHEDULING_CANCELLATION", "User wants to cancel or reschedule an appointment"));
        types.add(createType("SCHEDULING_OTHER", "Scheduling-related issue that does not clearly fit cancellation or rescheduling"));
        types.add(createType("ACCOUNT_ACCESS", "Problems logging in, password reset, or account access"));
        types.add(createType("SUPPORT_OTHER", "General support question not clearly related to billing or scheduling"));
        types.add(createType("BUG_APP", "Bug or error in the user-facing application or UI"));
        types.add(createType("BUG_BACKEND", "Bug or error in backend systems, APIs, or data processing"));
        types.add(createType("ENGINEERING_OTHER", "Engineering-related issue that does not clearly fit a known bug category"));
        types.add(createType("OTHER", "AI cannot confidently classify this request and a human dispatcher must decide"));
        
        return types;
    }
    
    private static TriageRequestDto.AllowedTicketType createType(String type, String description) {
        TriageRequestDto.AllowedTicketType t = new TriageRequestDto.AllowedTicketType();
        t.type = type;
        t.description = description;
        return t;
    }
}
