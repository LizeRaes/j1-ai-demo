package com.example.appointment.external;

import com.example.appointment.dto.TicketSyncUpsertDto;
import com.example.appointment.dto.TriageRequestDto;
import com.example.appointment.dto.TriageResponseDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@Path("/api")
@RegisterRestClient(configKey = "triage")
public interface TriageClient {

    @POST
    @Path("/triage/v1/classify")
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(10000)
    CompletionStage<TriageResponseDto> classifyAsync(TriageRequestDto request);

    @GET
    @Path("/documents/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, String> getDocumentContent(@PathParam("documentName") String documentName);

    @GET
    @Path("/documents/download/{documentName}")
    @Produces(MediaType.WILDCARD)
    Response downloadDocument(@PathParam("documentName") String documentName);

    @POST
    @Path("/ticket-sync/upsert")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> notifyTicketUpsert(TicketSyncUpsertDto request);

    @DELETE
    @Path("/ticket-sync/delete/{ticketId}")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> notifyTicketDelete(@PathParam("ticketId") Long ticketId);

}