package com.example.ticket.service;

import com.example.ticket.dto.TriageRequestDto;
import com.example.ticket.dto.TriageResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
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

    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private final HttpClient httpClient;
    private final Executor executor;
    @ConfigProperty(name = "ai-triage.url", defaultValue = "http://localhost:8081")
    String aiTriageUrl;

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
     * Build the list of allowed ticket types with descriptions.
     * This is fixed and always the same.
     */
    public static List<TriageRequestDto.AllowedTicketType> buildAllowedTicketTypes() {
        List<TriageRequestDto.AllowedTicketType> types = new ArrayList<>();

        types.add(new TriageRequestDto.AllowedTicketType("BILLING_REFUND", "User is asking for a refund or billing reimbursement"));
        types.add(new TriageRequestDto.AllowedTicketType("BILLING_OTHER", "Other billing-related issue requiring human review"));
        types.add(new TriageRequestDto.AllowedTicketType("SCHEDULING_CANCELLATION", "User wants to cancel or reschedule an appointment"));
        types.add(new TriageRequestDto.AllowedTicketType("SCHEDULING_OTHER", "Scheduling-related issue that does not clearly fit cancellation or rescheduling"));
        types.add(new TriageRequestDto.AllowedTicketType("ACCOUNT_ACCESS", "Problems logging in, password reset, or account access"));
        types.add(new TriageRequestDto.AllowedTicketType("SUPPORT_OTHER", "General support question not clearly related to billing or scheduling"));
        types.add(new TriageRequestDto.AllowedTicketType("BUG_APP", "Bug or error in the user-facing application or UI"));
        types.add(new TriageRequestDto.AllowedTicketType("BUG_BACKEND", "Bug or error in backend systems, APIs, or data processing"));
        types.add(new TriageRequestDto.AllowedTicketType("ENGINEERING_OTHER", "Engineering-related issue that does not clearly fit a known bug category"));
        types.add(new TriageRequestDto.AllowedTicketType("OTHER", "AI cannot confidently classify this request and a human dispatcher must decide"));

        return types;
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
                ObjectMapper objectMapper = new ObjectMapper();
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
                    return new TriageResponseDto("FAILED", null, null, null, null, null, "AI triage service returned status " + response.statusCode());
                }
            } catch (java.net.http.HttpTimeoutException e) {
                // Timeout - return failure response
                return new TriageResponseDto("FAILED", null, null, null, null, null, "AI triage service timeout after 3 minutes");
            } catch (java.net.ConnectException | java.net.UnknownHostException e) {
                // Connection refused or host unknown - service is down
                return new TriageResponseDto("FAILED", null, null, null, null, null, "AI triage service unavailable: " + e.getMessage());
            } catch (Exception e) {
                // Any other exception - return failure response
                return new TriageResponseDto("FAILED", null, null, null, null, null, "AI triage service error: " + e.getMessage());
            }
        }, executor);
    }

}
