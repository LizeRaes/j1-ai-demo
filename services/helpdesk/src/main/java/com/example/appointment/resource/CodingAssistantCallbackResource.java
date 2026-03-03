package com.example.ticket.resource;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import com.example.ticket.dto.CodingAssistantCallbackDto;
import com.example.ticket.service.TicketService;
import com.example.ticket.service.adapter.EventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/api/coding-assistant")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CodingAssistantCallbackResource {

    @Inject
    TicketService ticketService;

    @Inject
    EventService eventService;

    @ConfigProperty(name = "app.coding-assistant.callback.auth-token")
    String callbackAuthToken;

    @POST
    public Response receiveCallback(CodingAssistantCallbackDto callbackDto,
                                    @HeaderParam("Authorization") String authorization) {
        if (!isAuthorized(authorization)) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "coding-assistant-callback",
                    "Rejected coding assistant callback: invalid Authorization token.",
                    callbackDto != null ? callbackDto.ticketId() : null,
                    null,
                    null
            );
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("status", "UNAUTHORIZED", "message", "Invalid callback token"))
                    .build();
        }
        try {
            eventService.logEvent(
                    EventType.SYSTEM_STEP,
                    EventSeverity.INFO,
                    "coding-assistant-callback",
                    "Received coding assistant callback for ticket #" + (callbackDto != null ? callbackDto.ticketId() : null),
                    callbackDto != null ? callbackDto.ticketId() : null,
                    null,
                    null
            );
            ticketService.upsertAiPullRequest(
                    callbackDto != null ? callbackDto.ticketId() : null,
                    callbackDto != null ? callbackDto.prUrl() : null
            );
            return Response.ok(Map.of("status", "OK")).build();
        } catch (Exception e) {
            eventService.logEvent(
                    EventType.ERROR_OCCURRED,
                    EventSeverity.WARNING,
                    "coding-assistant-callback",
                    "Callback processing failed for ticket #" + (callbackDto != null ? callbackDto.ticketId() : null) + ": " + (e.getMessage() != null ? e.getMessage() : "Unexpected error"),
                    callbackDto != null ? callbackDto.ticketId() : null,
                    null,
                    null
            );
            return Response.ok(Map.of("status", "IGNORED", "message", e.getMessage() != null ? e.getMessage() : "Unexpected error")).build();
        }
    }

    private boolean isAuthorized(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        String expectedHeader = callbackAuthToken.startsWith("Bearer ")
                ? callbackAuthToken
                : "Bearer " + callbackAuthToken;
        return expectedHeader.equals(authorization);
    }
}
