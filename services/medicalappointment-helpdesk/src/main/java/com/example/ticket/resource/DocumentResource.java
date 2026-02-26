package com.example.ticket.resource;

import com.example.ticket.external.InternalDocumentClient;
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


    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDocumentContent(@PathParam("documentName") String documentName) {
        Map<String, String> internalDocumentContent = internalDocumentClient.getDocumentContent(documentName);
        if (internalDocumentContent.containsKey("content")) {
            return Response.ok(internalDocumentContent.get("content"))
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        return Response.status(Response.Status.NO_CONTENT)
                .entity("Failed to fetch document: " + documentName)
                .build();
    }
}
