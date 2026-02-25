package com.example.ticket.resource;

import com.example.ticket.service.TriageWorkerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/triage-worker")
@Produces(MediaType.APPLICATION_JSON)
public class TriageWorkerResource {
    @Inject
    TriageWorkerService triageWorkerService;

    @POST
    @Path("/process")
    public Response processNewRequests() {
        try {
            triageWorkerService.processNewRequests();
            return Response.ok().entity("{\"message\": \"Triage worker processed new requests\"}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
}
