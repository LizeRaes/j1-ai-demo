package com.example.document.resource;

import com.example.document.dto.*;
import com.example.document.service.DocumentAccessPolicyService;
import com.example.document.service.DocumentSearchService;
import com.example.document.service.DocumentService;
import com.example.document.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DocumentResource {

    @Inject
    DocumentService documentService;

    @Inject
    DocumentSearchService searchService;

    @Inject
    DocumentAccessPolicyService accessPolicyService;

    @Inject
    LogService logService;

    @POST
    @Path("/search")
    public DocumentSearchResponse search(DocumentSearchRequest request) {
        if (request.getOriginalText() == null) {
            throw new IllegalArgumentException("originalText is required");
        }

        int maxResults = request.getMaxResults() != null ? request.getMaxResults() : 5;
        double minScore = request.getMinScore() != null ? request.getMinScore() : 0.0;

        logService.addLog("Document search request: \"" +
                (request.getOriginalText().length() > 50 ?
                 request.getOriginalText().substring(0, 50) + "..." :
                 request.getOriginalText()) +
                "\", minScore: " + minScore, "search");

        var searchResults = searchService.searchDocuments(
                request.getOriginalText(),
                maxResults,
                minScore
        );

        List<DocumentSearchResponse.DocumentResult> results = searchResults.stream()
                .map(r -> new DocumentSearchResponse.DocumentResult(
                        r.documentName,
                        r.documentLink,
                        r.citation,
                        r.score,
                        r.rbacTeams
                ))
                .collect(Collectors.toList());

        logService.addLog("Returned " + results.size() + " document results", "search");

        return new DocumentSearchResponse(results);
    }

    @POST
    @Path("/upsert")
    public StatusResponse upsert(DocumentUpsertRequest request) {
        if (request.getDocumentName() == null || request.getContent() == null) {
            throw new IllegalArgumentException("documentName and content are required");
        }

        logService.addLog("Upsert document: " + request.getDocumentName(), "upsert");

        try {
            // If RBAC teams not specified, use document-wide (empty list)
            List<String> rbacTeams = request.getRbacTeams() != null ?
                    request.getRbacTeams() :
                    List.of(); // Empty list means document-wide

            documentService.upsertDocument(
                    request.getDocumentName(),
                    request.getContent(),
                    rbacTeams
            );

            logService.addLog("Successfully upserted document: " + request.getDocumentName(), "upsert");
            return new StatusResponse("OK");
        } catch (Exception e) {
            logService.addLog("Error upserting document " + request.getDocumentName() + ": " + e.getMessage(), "error");
            throw new RuntimeException("Failed to upsert document: " + e.getMessage(), e);
        }
    }

    @POST
    @Path("/delete")
    public StatusResponse delete(DocumentDeleteRequest request) {
        if (request.getDocumentName() == null) {
            throw new IllegalArgumentException("documentName is required");
        }

        logService.addLog("Delete document: " + request.getDocumentName(), "delete");

        documentService.deleteDocumentEmbeddings(request.getDocumentName());
        accessPolicyService.removeDocument(request.getDocumentName());

        logService.addLog("Successfully deleted document: " + request.getDocumentName(), "delete");
        return new StatusResponse("OK");
    }

    @POST
    @Path("/rbac/update")
    public StatusResponse updateRbac(DocumentRbacUpdateRequest request) {
        if (request.getDocumentName() == null) {
            throw new IllegalArgumentException("documentName is required");
        }

        logService.addLog("Update RBAC for document: " + request.getDocumentName(), "rbac");

        List<String> rbacTeams = request.getRbacTeams() != null ?
                request.getRbacTeams() :
                List.of(); // Empty list means document-wide

        accessPolicyService.updateDocumentAccess(request.getDocumentName(), rbacTeams);

        logService.addLog("Successfully updated RBAC for document: " + request.getDocumentName(), "rbac");
        return new StatusResponse("OK");
    }

    @GET
    @Path("/all")
    public DocumentsResponse getAllDocuments() {
        List<String> documentNames = documentService.getAllDocumentNames();

        List<DocumentsResponse.DocumentInfo> documents = documentNames.stream()
                .map(name -> {
                    List<String> teams = accessPolicyService.getAccessTeams(name);
                    String link = "/documents/" + name;
                    return new DocumentsResponse.DocumentInfo(name, link, teams);
                })
                .collect(Collectors.toList());

        return new DocumentsResponse(documents);
    }

    @GET
    @Path("/logs")
    public com.example.document.dto.LogsResponse getLogs() {
        var logs = logService.getLogs().stream()
                .map(l -> new com.example.document.dto.LogsResponse.LogInfo(l.message, l.type, l.timestamp))
                .collect(Collectors.toList());

        return new com.example.document.dto.LogsResponse(logs);
    }

    @GET
    @Path("/config")
    public java.util.Map<String, Object> getConfig() {
        return java.util.Map.of("defaultZoom", 100);
    }

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, String> getDocumentContent(@PathParam("documentName") String documentName) {
        try {
            // Read document from static
            java.io.InputStream docStream = getClass().getClassLoader()
                .getResourceAsStream("documents/" + documentName);

            if (docStream == null) {
                throw new jakarta.ws.rs.NotFoundException("Document not found: " + documentName);
            }

            String content = new String(docStream.readAllBytes());
            docStream.close();

            return java.util.Map.of("content", content);
        } catch (Exception e) {
            throw new jakarta.ws.rs.InternalServerErrorException("Error reading document: " + e.getMessage());
        }
    }

    @GET
    @Path("/chunks")
    public ChunksResponse getAllChunks() {
        var chunks = searchService.getAllChunks();

        List<ChunksResponse.ChunkInfo> chunkInfos = chunks.stream()
                .map(c -> new ChunksResponse.ChunkInfo(
                        c.documentName,
                        c.chunkIndex,
                        c.text,
                        c.vector
                ))
                .collect(Collectors.toList());

        return new ChunksResponse(chunkInfos);
    }
}
