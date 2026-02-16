package com.mediflow.ticketing.api;

import com.mediflow.ticketing.dto.CreateIncomingRequestDto;
import com.mediflow.ticketing.dto.IntakeRequestDto;
import com.mediflow.ticketing.dto.IncomingRequestDto;
import com.mediflow.ticketing.service.IncomingRequestService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/intake")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntakeResource {
    @Inject
    IncomingRequestService incomingRequestService;

    @POST
    @Path("/incoming-request")
    public Response createIncomingRequest(IntakeRequestDto intakeDto) {
        // Validate input
        if (intakeDto.userId == null || intakeDto.userId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("userId is required").build();
        }
        if (intakeDto.message == null || intakeDto.message.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("message is required").build();
        }

        // Forward to existing incoming request service
        CreateIncomingRequestDto dto = new CreateIncomingRequestDto();
        dto.userId = intakeDto.userId.trim();
        dto.channel = "intake-ui";
        dto.rawText = intakeDto.message.trim();

        IncomingRequestDto created = incomingRequestService.createIncomingRequest(dto);
        
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
