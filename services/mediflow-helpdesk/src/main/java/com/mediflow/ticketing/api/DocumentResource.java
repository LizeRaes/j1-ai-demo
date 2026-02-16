package com.mediflow.ticketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Path("/documents")
public class DocumentResource {
    
    private static final String DOCUMENTS_API_BASE = "http://localhost:8084/api/documents";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDocumentContent(@PathParam("documentName") String documentName) {
        try {
            // Build the URL to the external documents API
            String url = DOCUMENTS_API_BASE + "/content/" + documentName;
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            
            // Execute request
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check response status
            if (response.statusCode() == 200) {
                String body = response.body();
                
                // Check if response is JSON (documents API returns JSON with "content" field)
                if (body.trim().startsWith("{") && body.contains("\"content\"")) {
                    try {
                        // Parse JSON and extract content field
                        JsonNode jsonNode = objectMapper.readTree(body);
                        JsonNode contentNode = jsonNode.get("content");
                        if (contentNode != null && contentNode.isTextual()) {
                            body = contentNode.asText();
                        }
                    } catch (Exception e) {
                        // If parsing fails, return as-is
                        // Log but don't fail
                    }
                }
                
                return Response.ok(body)
                        .type(MediaType.TEXT_PLAIN)
                        .build();
            } else {
                return Response.status(response.statusCode())
                        .entity("Failed to fetch document: " + response.statusCode())
                        .build();
            }
        } catch (IOException | InterruptedException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching document: " + e.getMessage())
                    .build();
        }
    }
}
