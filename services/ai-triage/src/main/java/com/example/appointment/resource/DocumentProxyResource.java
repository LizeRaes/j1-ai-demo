package com.medicalappointment.triage.resource;

import com.medicalappointment.triage.external.InternalDocumentClient;
import com.medicalappointment.triage.service.EventLogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@Path("/api/ui/documents")
public class DocumentProxyResource {

    @Inject
    EventLogService eventLogService;

    @Inject
    @RestClient
    InternalDocumentClient internalDocumentClient;

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyDocumentContent(@PathParam("documentName") String documentName) {
        try (Response upstream = internalDocumentClient.getDocumentContent(documentName)) {
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

    @GET
    @Path("/download/{documentName}")
    public Response proxyDocumentDownload(@PathParam("documentName") String documentName) {
        try (Response upstream = internalDocumentClient.downloadDocument(documentName)) {
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

