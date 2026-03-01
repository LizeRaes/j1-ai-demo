package com.example.aicodingassistant.api;

import com.example.aicodingassistant.dto.SubmitJobRequest;
import com.example.aicodingassistant.dto.SubmitJobResponse;
import com.example.aicodingassistant.logging.JobLogService;
import com.example.aicodingassistant.logging.JobLogStream;
import com.example.aicodingassistant.service.JobOrchestratorService;
import com.example.aicodingassistant.validation.RequestValidation;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/coding-assistant")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CodingAssistantResource {

    @Inject
    JobOrchestratorService orchestratorService;

    @Inject
    JobLogStream jobLogStream;

    @POST
    @Path("/jobs")
    public Response submitJob(SubmitJobRequest request) {
        Response validation = RequestValidation.validate(request);
        if (validation != null) {
            return validation;
        }

        SubmitJobResponse response = orchestratorService.submit(request);
        return Response.accepted(response).build();
    }

    @GET
    @Path("/logs/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<JobLogService.LogEntry> streamLogs() {
        return jobLogStream.stream();
    }
}
