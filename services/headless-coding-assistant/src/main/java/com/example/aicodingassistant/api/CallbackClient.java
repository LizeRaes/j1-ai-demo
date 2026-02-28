package com.example.aicodingassistant.api;

import com.example.aicodingassistant.dto.CallbackResultRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

@ApplicationScoped
public class CallbackClient {
    @ConfigProperty(name = "app.callback.max-retries")
    int maxRetries;

    @ConfigProperty(name = "app.callback.retry-delay-millis")
    long retryDelayMillis;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Inject
    public CallbackClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    public void postResult(String callbackUrl, String callbackAuthToken, CallbackResultRequest payload, Consumer<String> logger)
            throws IOException, InterruptedException {
        // callbackUrl is dynamic per request, so we keep a plain HTTP client instead of a fixed Quarkus REST client.
        String body = objectMapper.writeValueAsString(payload);
        logger.accept("Callback target: " + callbackUrl);
        logger.accept("Callback payload: " + body);
        String authHeader = callbackAuthToken.startsWith("Bearer ")
                ? callbackAuthToken
                : "Bearer " + callbackAuthToken;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader)
                .header("Idempotency-Key", payload.jobId())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    logger.accept("Callback delivered for job " + payload.jobId() + " with status " + statusCode);
                    return;
                }
                throw new IllegalStateException("Callback failed with HTTP " + statusCode);
            } catch (Exception e) {
                lastFailure = e;
                logger.accept("Callback attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(retryDelayMillis);
                }
            }
        }

        if (lastFailure instanceof IOException ioException) {
            throw ioException;
        }
        if (lastFailure instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        throw new IllegalStateException("Callback delivery failed after retries.", lastFailure);
    }
}
