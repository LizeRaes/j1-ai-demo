package com.example.appointment.resource;

import com.example.appointment.dto.DispatchedTicketDto;
import com.example.appointment.dto.TicketDto;
import com.example.appointment.service.TicketService;
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
    TicketService ticketService;

    @POST
    @Path("/submit-ticket")
    public Response submitTicket(DispatchedTicketDto dto) {
        try {
            TicketDto ticket = ticketService.submit(dto);
            return Response.status(Response.Status.CREATED).entity(ticket).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
