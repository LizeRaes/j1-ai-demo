package com.mediflow.triage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.triage.dto.SimilaritySearchRequest;
import com.mediflow.triage.dto.SimilaritySearchResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class SimilarityService {

    @ConfigProperty(name = "ai-triage.similarity.base-url", defaultValue = "http://localhost:8082")
    String baseUrl;

    @ConfigProperty(name = "ai-triage.similarity.timeout", defaultValue = "5S")
    Duration timeout;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public SimilarityService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public SimilaritySearchResponse searchSimilarTickets(String ticketType, String text, Integer maxResults, Integer ticketId) 
            throws SimilarityServiceException {
        try {
            SimilaritySearchRequest request = new SimilaritySearchRequest(ticketType, text, maxResults, ticketId);
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/similarity/tickets/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(timeout)
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), SimilaritySearchResponse.class);
            } else {
                String errorMsg = String.format("HTTP %d: %s", response.statusCode(), response.body());
                throw new SimilarityServiceException(errorMsg, response.statusCode());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SimilarityServiceException("Timeout: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        } catch (java.net.ConnectException e) {
            throw new SimilarityServiceException("Connection failed: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new SimilarityServiceException("IO error: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        } catch (Exception e) {
            throw new SimilarityServiceException("Unexpected error: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        }
    }

    public static class SimilarityServiceException extends Exception {
        private final Integer statusCode;

        public SimilarityServiceException(String message) {
            super(message);
            this.statusCode = null;
        }

        public SimilarityServiceException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = null;
        }

        public SimilarityServiceException(String message, Integer statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public String getFullErrorInfo() {
            String errorType = getClass().getSimpleName();
            String errorMessage = getMessage();
            if (getCause() != null) {
                errorType += " (caused by: " + getCause().getClass().getSimpleName() + ")";
            }
            return errorType + ", " + errorMessage;
        }
    }
}
