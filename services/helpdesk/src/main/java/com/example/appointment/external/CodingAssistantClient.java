package com.example.appointment.external;

import com.example.appointment.dto.CodingAssistantSubmitJobRequestDto;
import com.example.appointment.dto.CodingAssistantSubmitJobResponseDto;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/coding-assistant")
@RegisterRestClient(configKey = "coding-assistant")
public interface CodingAssistantClient {

    @POST
    @Path("/jobs")
    @Produces(MediaType.APPLICATION_JSON)
    @Retry(maxRetries = 1, delay = 300)
    @Timeout(500)
    CodingAssistantSubmitJobResponseDto submitJob(CodingAssistantSubmitJobRequestDto request);
}
