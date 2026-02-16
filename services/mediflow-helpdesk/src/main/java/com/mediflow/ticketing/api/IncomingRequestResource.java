package com.mediflow.ticketing.api;

import com.mediflow.ticketing.domain.enums.RequestStatus;
import com.mediflow.ticketing.dto.CreateIncomingRequestDto;
import com.mediflow.ticketing.dto.IncomingRequestDto;
import com.mediflow.ticketing.service.IncomingRequestService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/incoming-requests")
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
