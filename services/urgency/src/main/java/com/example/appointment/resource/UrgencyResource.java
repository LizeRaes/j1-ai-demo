package com.example.appointment.resource;

import com.example.appointment.service.UrgencyInferenceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/urgency/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UrgencyResource {

    @Inject
    UrgencyInferenceService urgencyInferenceService;

    @POST
    @Path("/score")
    public UrgencyScoreResponse score(UrgencyScoreRequest request) {
        return new UrgencyScoreResponse(urgencyInferenceService.score(request.complaint()));
    }

    public record UrgencyScoreRequest(String complaint) {
    }

    public record UrgencyScoreResponse(double score) {
    }
}
