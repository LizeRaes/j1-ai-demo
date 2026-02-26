package com.example.ticket.resource;

import com.example.ticket.dto.DispatchCreateTicketDto;
import com.example.ticket.dto.TicketDto;
import com.example.ticket.service.DispatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/dispatch")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DispatchResource {
    @Inject
    DispatchService dispatchService;

    @POST
    @Path("/submit-ticket")
    public Response submitTicket(DispatchCreateTicketDto dto) {
        try {
            TicketDto ticket = dispatchService.submitTicket(dto);
            return Response.status(Response.Status.CREATED).entity(ticket).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
