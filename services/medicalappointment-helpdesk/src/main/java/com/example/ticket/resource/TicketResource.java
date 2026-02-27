package com.example.ticket.resource;

import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.dto.*;
import com.example.ticket.service.TicketService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/tickets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TicketResource {
    @Inject
    TicketService ticketService;

    @POST
    @Path("/manual")
    public Response createTicketManual(ManualTicketDto dto) {
        TicketDto ticket = ticketService.manualSubmit(dto);
        return Response.status(Response.Status.CREATED).entity(ticket).build();
    }

    @POST
    @Path("/from-ai")
    public Response createTicketFromAi(AITicketDto dto) {
        TicketDto ticket = ticketService.aiSubmit(dto);
        return Response.status(Response.Status.CREATED).entity(ticket).build();
    }

    @GET
    public List<TicketDto> getTickets(
            @QueryParam("view") String view,
            @QueryParam("team") String team,
            @QueryParam("user") String user) {
        return ticketService.findTickets(view, team, user);
    }

    @GET
    @Path("/{id}")
    public Response getTicket(@PathParam("id") Long id) {
        TicketDto ticket = ticketService.findTicket(id);
        if (ticket == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(ticket).build();
    }

    @POST
    @Path("/{id}/accept")
    public Response acceptTicket(@PathParam("id") Long id, @QueryParam("userId") String userId) {
        TicketDto ticket = ticketService.acceptTicket(id, userId);
        if (ticket == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(ticket).build();
    }

    @POST
    @Path("/{id}/reject-and-return-to-dispatch")
    public Response rejectAndReturnToDispatch(@PathParam("id") Long id) {
        TicketDto ticket = ticketService.rejectAndReturnToDispatch(id);
        if (ticket == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(ticket).build();
    }

    @POST
    @Path("/{id}/status")
    public Response updateTicketStatus(@PathParam("id") Long id, UpdateTicketStatusDto dto) {
        TicketDto ticket = ticketService.updateTicketStatus(id, dto.status());
        if (ticket == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(ticket).build();
    }

    @POST
    @Path("/{id}/ticket-type")
    public Response updateTicketType(@PathParam("id") Long id, UpdateTicketTypeDto dto) {
        try {
            TicketDto ticket = ticketService.updateTicketType(id, dto);
            if (ticket == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(ticket).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/comments")
    public Response addComment(@PathParam("id") Long id, AddCommentDto dto) {
        CommentDto comment = ticketService.addComment(id, dto);
        if (comment == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.status(Response.Status.CREATED).entity(comment).build();
    }

    @POST
    @Path("/{id}/rollback-to-request")
    public Response rollbackToRequest(@PathParam("id") Long id) {
        try {
            TicketDto ticket = ticketService.rollbackTicket(id);
            if (ticket == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(ticket).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
