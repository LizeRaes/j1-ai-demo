package com.example.document.resource;

import com.example.document.dto.*;
import com.example.document.service.DocumentAccessPolicyService;
import com.example.document.service.DocumentSearchService;
import com.example.document.service.DocumentService;
import com.example.document.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

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
        if (request.originalText() == null) {
            throw new IllegalArgumentException("originalText is required");
        }

        int maxResults = request.maxResults() != null ? request.maxResults() : 5;
        double minScore = request.minScore() != null ? request.minScore() : 0.0;

        logService.addLog("Document search request: \"" +
                (request.originalText().length() > 50 ?
                        request.originalText().substring(0, 50) + "..." :
                        request.originalText()) +
                "\", minScore: " + minScore, "search");

        var searchResults = searchService.searchDocuments(
                request.originalText(),
                maxResults,
                minScore
        );

        List<DocumentSearchResponse.DocumentResult> results = searchResults.stream()
                .map(r -> new DocumentSearchResponse.DocumentResult(
                        r.documentName(),
                        r.documentLink(),
                        r.citation(),
                        r.score(),
                        r.rbacTeams()
                ))
                .collect(toList());

        logService.addLog("Returned " + results.size() + " document results", "search");

        return new DocumentSearchResponse(results);
    }

    @POST
    @Path("/upsert")
    public StatusResponse upsert(DocumentUpsertRequest request) {
        if (request.documentName() == null || request.content() == null) {
            throw new IllegalArgumentException("documentName and content are required");
        }

        logService.addLog("Upsert document: " + request.documentName(), "upsert");

        try {
            // If RBAC teams not specified, use document-wide (empty list)
            List<String> rbacTeams = request.rbacTeams() != null ?
                    request.rbacTeams() :
                    List.of(); // Empty list means document-wide

            documentService.upsertDocument(
                    request.documentName(),
                    request.content(),
                    rbacTeams
            );

            logService.addLog("Successfully upserted document: " + request.documentName(), "upsert");
            return new StatusResponse("OK");
        } catch (Exception e) {
            logService.addLog("Error upserting document " + request.documentName() + ": " + e.getMessage(), "error");
            throw new RuntimeException("Failed to upsert document: " + e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/{documentName}")
    public StatusResponse deleteByName(@PathParam("documentName") String documentName) {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("documentName is required");
        }
        logService.addLog("Delete document: " + documentName, "delete");
        documentService.deleteDocumentEmbeddings(documentName);
        logService.addLog("Successfully deleted document: " + documentName, "delete");
        return new StatusResponse("OK");
    }

    @GET
    @Path("/all")
    public DocumentsResponse getAllDocuments() {
        List<DocumentsResponse.DocumentInfo> documents= documentService.findAllDocuments();
        return new DocumentsResponse(documents);
    }

    @GET
    @Path("/logs")
    public LogsResponse getLogs() {
        var logs = logService.getLogs().stream()
                .map(l -> new LogsResponse.LogInfo(l.message(), l.type(), l.timestamp()))
                .collect(toList());

        return new LogsResponse(logs);
    }

    @GET
    @Path("/config")
    public Map<String, Object> getConfig() {
        return java.util.Map.of("defaultZoom", 100);
    }

    @GET
    @Path("/content/{documentName}")
    @Produces(MediaType.APPLICATION_JSON)
    public java.util.Map<String, String> getDocumentContent(@PathParam("documentName") String documentName) {
        try {
            InputStream docStream = getClass().getClassLoader()
                    .getResourceAsStream("documents/" + documentName);

            if (docStream == null) {
                throw new jakarta.ws.rs.NotFoundException("Document not found: " + documentName);
            }

            String content = new String(docStream.readAllBytes());
            docStream.close();

            return Map.of("content", content);
        } catch (Exception e) {
            throw new InternalServerErrorException("Error reading document: " + e.getMessage());
        }
    }

    @GET
    @Path("/chunks")
    public ChunksResponse getAllChunks() {
        var chunks = searchService.getAllChunks();

        List<ChunksResponse.ChunkInfo> chunkInfos = chunks.stream()
                .map(c -> new ChunksResponse.ChunkInfo(
                        c.documentName(),
                        c.chunkIndex(),
                        c.text(),
                        c.vector()
                ))
                .collect(toList());

        return new ChunksResponse(chunkInfos);
    }

    @POST
    @Path("/rbac/update")
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
