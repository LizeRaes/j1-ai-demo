package com.medicalappointment.ticketing.api;

import com.medicalappointment.ticketing.service.TriageWorkerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/triage-worker")
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
