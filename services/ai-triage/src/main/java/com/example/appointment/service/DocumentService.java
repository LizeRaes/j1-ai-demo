package com.example.appointment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.appointment.dto.DocumentSearchRequest;
import com.example.appointment.dto.DocumentSearchResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class DocumentService {

    @ConfigProperty(name = "ai-triage.documents.base-url", defaultValue = "http://localhost:8084")
    String baseUrl;

    @ConfigProperty(name = "ai-triage.documents.timeout", defaultValue = "5S")
    Duration timeout;

    @ConfigProperty(name = "ai-triage.documents.max-results", defaultValue = "5")
    Integer maxResults;

    @ConfigProperty(name = "ai-triage.documents.min-score", defaultValue = "0.3")
    Double minScore;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public DocumentService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public DocumentSearchResponse searchDocuments(String originalText) throws DocumentServiceException {
        return searchDocuments(originalText, maxResults, minScore);
    }

    public DocumentSearchResponse searchDocuments(String originalText, Integer maxResults, Double minScore) 
            throws DocumentServiceException {
        try {
            DocumentSearchRequest request = new DocumentSearchRequest(originalText, maxResults, minScore);
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(timeout)
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), DocumentSearchResponse.class);
            } else {
                String errorMsg = String.format("HTTP %d: %s", response.statusCode(), response.body());
                throw new DocumentServiceException(errorMsg, response.statusCode());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw new DocumentServiceException("Timeout: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        } catch (java.net.ConnectException e) {
            throw new DocumentServiceException("Connection failed: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new DocumentServiceException("IO error: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        } catch (Exception e) {
            throw new DocumentServiceException("Unexpected error: " + e.getClass().getSimpleName() + ", " + e.getMessage(), e);
        }
    }

    public static class DocumentServiceException extends Exception {
        private final Integer statusCode;

        public DocumentServiceException(String message) {
            super(message);
            this.statusCode = null;
        }

        public DocumentServiceException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = null;
        }

        public DocumentServiceException(String message, Integer statusCode) {
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
