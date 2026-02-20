package com.example.document.resource;

import com.example.document.dto.DocumentRbacUpdateRequest;
import com.example.document.dto.StatusResponse;
import com.example.document.service.DocumentAccessPolicyService;
import com.example.document.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/rbac")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccessResource {

    @Inject
    DocumentAccessPolicyService accessPolicyService;

    @Inject
    LogService logService;

    @POST
    @Path("/update")
    public StatusResponse update(DocumentRbacUpdateRequest request) {
        if (request.documentName() == null) {
            throw new IllegalArgumentException("documentName is required");
        }

        logService.addLog("Update RBAC for document: " + request.documentName(), "rbac");

        List<String> rbacTeams = request.rbacTeams() != null ?
                request.rbacTeams() :
                List.of(); // Empty list means document-wide

        accessPolicyService.updateDocumentAccess(request.documentName(), rbacTeams);

        logService.addLog("Successfully updated RBAC for document: " + request.documentName(), "rbac");
        return new StatusResponse("OK");
    }
}
