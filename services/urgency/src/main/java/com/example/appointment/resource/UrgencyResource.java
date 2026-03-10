package com.example.appointment.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/urgency/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UrgencyResource {

    @POST
    @Path("/score")
    public UrgencyScoreResponse score(UrgencyScoreRequest request) {
        // TODO Placeholder until urgency model bug is fixed.
        return new UrgencyScoreResponse(0.7);
    }

    public record UrgencyScoreRequest(String complaint) {
    }

    public record UrgencyScoreResponse(double score) {
    }
}
