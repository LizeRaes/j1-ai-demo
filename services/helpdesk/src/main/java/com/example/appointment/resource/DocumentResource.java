package com.example.appointment.resource;

import com.example.appointment.domain.constants.EventSeverity;
import com.example.appointment.domain.constants.EventType;
import com.example.appointment.external.InternalDocumentClient;
import com.example.appointment.service.adapter.EventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;

@Path("/api/documents")
public class DocumentResource {

    @Inject
    @RestClient
    InternalDocumentClient internalDocumentClient;

    @Inject
    EventService eventService;


    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDocumentContent(@PathParam("documentName") String documentName) {
        try {
            Map<String, String> internalDocumentContent = internalDocumentClient.getDocumentContent(documentName);
            if (internalDocumentContent.containsKey("content")) {
                return Response.ok(internalDocumentContent.get("content"))
                        .type(MediaType.TEXT_PLAIN)
                        .build();
            }
            return Response.status(Response.Status.NO_CONTENT)
                    .entity("Failed to fetch document: " + documentName)
                    .build();
        } catch (Exception e) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "documents-proxy",
                    "Document content fetch failed for " + documentName + ": " + e.getMessage(),
                    null,
                    null,
                    null
            );
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Document could not be retrieved right now")
                    .build();
        }
    }

    @GET
    @Path("/download/{documentName}")
    public Response downloadDocument(@PathParam("documentName") String documentName) {
        try {
            Response upstream = internalDocumentClient.downloadDocument(documentName);
            if (upstream.getStatus() >= 400) {
                return Response.status(upstream.getStatus()).build();
            }
            return Response.ok(upstream.readEntity(byte[].class))
                    .type(upstream.getMediaType())
                    .header("Content-Disposition", upstream.getHeaderString("Content-Disposition"))
                    .build();
        } catch (Exception e) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "documents-proxy",
                    "Document download failed for " + documentName + ": " + e.getMessage(),
                    null,
                    null,
                    null
            );
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Document could not be retrieved right now")
                    .build();
        }
    }
}
