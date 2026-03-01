package com.example.ticket.external;

import com.example.ticket.dto.TriageRequestDto;
import com.example.ticket.dto.TriageResponseDto;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.concurrent.CompletionStage;

@Path("/api/triage")
@RegisterRestClient(configKey = "triage")
public interface TriageClient {

    @POST
    @Path("/v1/classify")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 1, delay = 1000)
    @Timeout(30000)
    CompletionStage<TriageResponseDto> classifyAsync(TriageRequestDto request);

}