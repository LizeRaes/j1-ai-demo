package com.example.appointment.external;

import com.example.appointment.dto.DocumentSearchRequest;
import com.example.appointment.dto.DocumentSearchResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "company-rag-documents")
@Path("/api/documents")
public interface DocumentClient {

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    Response fetchDocument(@PathParam("documentName") String documentName);

    @GET
    @Path("/download/{documentName}")
    Response downloadDocument(@PathParam("documentName") String documentName);

    @POST
    @Path("/search")
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    DocumentSearchResponse search(DocumentSearchRequest request);
}

