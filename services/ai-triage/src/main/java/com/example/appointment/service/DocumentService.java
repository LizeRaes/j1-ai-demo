package com.example.appointment.service;

import com.example.appointment.dto.DocumentSearchRequest;
import com.example.appointment.dto.DocumentSearchResponse;
import com.example.appointment.external.DocumentClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@ApplicationScoped
public class DocumentService {

    @Inject
    @RestClient
    DocumentClient documentClient;

    @ConfigProperty(name = "ai-triage.documents.max-results")
    Integer maxResults;

    @ConfigProperty(name = "ai-triage.documents.min-score")
    Double minScore;

    @Inject
    EventLogService eventLogService;

    public DocumentSearchResponse searchDocuments(String originalText) {
        DocumentSearchRequest request = new DocumentSearchRequest(originalText, maxResults, minScore);
        return documentClient.search(request);
    }

    public Response fetchDocumentContent(String documentName) {
        try (Response upstream = documentClient.fetchDocument(documentName)) {
            int status = upstream.getStatus();
            String body = upstream.hasEntity() ? upstream.readEntity(String.class) : "{}";
            if (status >= 400) {
                eventLogService.addEvent("WARN", "Document content fetch failed for " + documentName + " (status " + status + ")");
            }
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build();
        } catch (Exception e) {
            eventLogService.addEvent("WARN", "Document content fetch failed for " + documentName + ": " + e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Document could not be retrieved"))
                    .build();
        }
    }

    public Response downloadDocument(String documentName) {
        try (Response upstream = documentClient.downloadDocument(documentName)) {
            int status = upstream.getStatus();
            byte[] body = upstream.hasEntity() ? upstream.readEntity(byte[].class) : new byte[0];
            if (status >= 400) {
                eventLogService.addEvent("WARN", "Document download failed for " + documentName + " (status " + status + ")");
            }

            String contentType = upstream.getHeaderString(HttpHeaders.CONTENT_TYPE);
            String contentDisposition = upstream.getHeaderString(HttpHeaders.CONTENT_DISPOSITION);

            Response.ResponseBuilder builder = Response.status(status)
                    .type(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                    .entity(body);

            if (contentDisposition != null && !contentDisposition.isBlank()) {
                builder.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
            }
            return builder.build();
        } catch (Exception e) {
            eventLogService.addEvent("WARN", "Document download failed for " + documentName + ": " + e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "Document could not be retrieved"))
                    .build();
        }
    }

}
