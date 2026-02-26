package com.example.ticket.resource;

import com.example.ticket.dto.CreateIncomingRequestDto;
import com.example.ticket.dto.IncomingRequestDto;
import com.example.ticket.dto.IntakeRequestDto;
import com.example.ticket.service.IncomingRequestService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/intake")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntakeResource {
    @Inject
    IncomingRequestService incomingRequestService;

    @POST
    @Path("/incoming-request")
    public Response createIncomingRequest(IntakeRequestDto intakeDto) {
        // Validate input
        if (intakeDto.userId() == null || intakeDto.userId().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("userId is required").build();
        }
        if (intakeDto.message() == null || intakeDto.message().trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("message is required").build();
        }

        // Forward to existing incoming request service
        CreateIncomingRequestDto dto = new CreateIncomingRequestDto(intakeDto.userId().trim(), "intake-ui", intakeDto.message().trim());

        IncomingRequestDto created = incomingRequestService.createIncomingRequest(dto);

        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
