package com.example.appointment.resource;

import com.example.appointment.service.DocumentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/documents")
public class DocumentResource {

    @Inject
    DocumentService documentService;

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyDocumentContent(@PathParam("documentName") String documentName) {
        return documentService.fetchDocumentContent(documentName);
    }

    @GET
    @Path("/download/{documentName}")
    public Response proxyDocumentDownload(@PathParam("documentName") String documentName) {
        return documentService.downloadDocument(documentName);
    }
}

