package com.medicalappointment.triage.external;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "company-rag-documents")
@Path("/api/documents")
public interface InternalDocumentClient {

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getDocumentContent(@PathParam("documentName") String documentName);

    @GET
    @Path("/download/{documentName}")
    Response downloadDocument(@PathParam("documentName") String documentName);
}

