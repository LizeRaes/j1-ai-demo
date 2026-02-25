package com.example.ticket.resource;

import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.dto.CreateIncomingRequestDto;
import com.example.ticket.dto.IncomingRequestDto;
import com.example.ticket.service.IncomingRequestService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/incoming-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IncomingRequestResource {
    @Inject
    IncomingRequestService incomingRequestService;

    @POST
    public Response createIncomingRequest(CreateIncomingRequestDto dto) {
        IncomingRequestDto created = incomingRequestService.createIncomingRequest(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    public List<IncomingRequestDto> getIncomingRequests(@QueryParam("status") RequestStatus status) {
        // Special handling for dispatcher inbox: return AI_TRIAGE_FAILED and RETURNED_FROM_AI
        // (NEW requests are being processed by AI triage and shouldn't appear here)
        if (status == null) {
            return incomingRequestService.getDispatcherInboxRequests();
        }
        return incomingRequestService.getIncomingRequests(status);
    }

    @GET
    @Path("/{id}")
    public Response getIncomingRequest(@PathParam("id") Long id) {
        IncomingRequestDto request = incomingRequestService.getIncomingRequest(id);
        if (request == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(request).build();
    }
}
