package com.example.appointment.external;

import com.example.appointment.dto.UrgencyScoreRequest;
import com.example.appointment.dto.UrgencyScoreResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "urgency")
@Path("/api/urgency/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UrgencyClient {

    @POST
    @Path("/score")
    @Timeout(value = 6, unit = ChronoUnit.SECONDS)
    UrgencyScoreResponse score(UrgencyScoreRequest request);
}
