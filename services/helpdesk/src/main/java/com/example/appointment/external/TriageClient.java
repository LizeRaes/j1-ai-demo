package com.example.appointment.external;

import com.example.appointment.dto.TriageRequestDto;
import com.example.appointment.dto.TriageResponseDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@Path("/api")
@RegisterRestClient(configKey = "triage")
public interface TriageClient {

    @POST
    @Path("/triage/v1/classify")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 1, delay = 1000)
    @Timeout(30000)
    CompletionStage<TriageResponseDto> classifyAsync(TriageRequestDto request);

    @GET
    @Path("/documents/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, String> getDocumentContent(@PathParam("documentName") String documentName);

    @GET
    @Path("/documents/download/{documentName}")
    @Produces(MediaType.WILDCARD)
    Response downloadDocument(@PathParam("documentName") String documentName);

}