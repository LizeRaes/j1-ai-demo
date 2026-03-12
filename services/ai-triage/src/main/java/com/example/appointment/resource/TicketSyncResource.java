package com.example.appointment.resource;

import com.example.appointment.dto.TicketSyncUpsertRequest;
import com.example.appointment.service.SimilarityService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/ticket-sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketSyncResource {

    @Inject
    SimilarityService similarityService;

    @POST
    @Path("/upsert")
    public Response notifyUpsert(TicketSyncUpsertRequest request) {
        if (request == null || request.ticketId() == null || request.ticketType() == null || request.text() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("status", "FAILED", "reason", "ticketId, ticketType, and text are required"))
                    .build();
        }
        similarityService.upsertTicket(request.ticketId(), request.ticketType(), request.text());
        return Response.ok(Map.of("status", "OK")).build();
    }

    @DELETE
    @Path("/delete/{ticketId}")
    public Response notifyDelete(@PathParam("ticketId") Long ticketId) {
        if (ticketId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("status", "FAILED", "reason", "ticketId is required"))
                    .build();
        }
        similarityService.deleteTicket(ticketId);
        return Response.ok(Map.of("status", "OK")).build();
    }
}
