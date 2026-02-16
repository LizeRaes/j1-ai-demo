package com.medicalappointment.ticketing.api;

import com.medicalappointment.ticketing.dto.DispatchCreateTicketDto;
import com.medicalappointment.ticketing.dto.TicketDto;
import com.medicalappointment.ticketing.service.DispatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/dispatch")
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
