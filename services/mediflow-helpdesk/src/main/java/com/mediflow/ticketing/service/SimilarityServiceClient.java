package com.mediflow.ticketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.ticketing.dto.SimilarityResponseDto;
import com.mediflow.ticketing.dto.SimilarityUpsertRequestDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Client for calling external ticket similarity service.
 * All calls are async and non-blocking.
 */
@ApplicationScoped
public class SimilarityServiceClient {
    
    @ConfigProperty(name = "similarity-service.url", defaultValue = "http://localhost:8082")
    String similarityServiceUrl;
    
    @Inject
    ObjectMapper objectMapper;
    
    private final HttpClient httpClient;
    private final Executor executor;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    public SimilarityServiceClient() {
        // Use a dedicated executor for similarity service calls to avoid blocking request threads
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "similarity-client-worker");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    /**
     * Upsert a ticket embedding to the similarity service asynchronously.
     * 
     * @param request The upsert request with ticketId, ticketType, and text
     * @return CompletableFuture that completes with the response or fails with an exception
     */
    public CompletableFuture<SimilarityResponseDto> upsertTicketAsync(SimilarityUpsertRequestDto request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestBody = objectMapper.writeValueAsString(request);
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(similarityServiceUrl + "/api/similarity/tickets/upsert"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(TIMEOUT)
                    .build();
                
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return objectMapper.readValue(response.body(), SimilarityResponseDto.class);
                } else {
                    // Non-200 status code - throw exception with error details
                    throw new RuntimeException("Similarity service returned status " + response.statusCode() + ": " + response.body());
                }
            } catch (java.net.http.HttpTimeoutException e) {
                throw new RuntimeException("Similarity service timeout after 30 seconds", e);
            } catch (java.net.ConnectException | java.net.UnknownHostException e) {
                throw new RuntimeException("Similarity service unavailable: " + e.getMessage(), e);
            } catch (Exception e) {
                throw new RuntimeException("Similarity service error: " + e.getMessage(), e);
            }
        }, executor);
    }
}
