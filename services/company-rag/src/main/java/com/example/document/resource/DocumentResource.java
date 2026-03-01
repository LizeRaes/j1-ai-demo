package com.example.document.resource;

import com.example.document.dto.*;
import com.example.document.service.DocumentAccessPolicyService;
import com.example.document.service.DocumentSearchService;
import com.example.document.service.DocumentService;
import com.example.document.service.LogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
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
            List<String> rbacTeams = request.rbacTeams() != null
                    ? request.rbacTeams()
                    : resolveDefaultRbacTeams(request.documentName());

            documentService.upsertDocumentFile(
                    request.documentName(),
                    request.content().getBytes(StandardCharsets.UTF_8),
                    rbacTeams
            );

            logService.addLog("Successfully upserted document: " + request.documentName(), "upsert");
            return new StatusResponse("OK");
        } catch (Exception e) {
            logService.addLog("Error upserting document " + request.documentName() + ": " + e.getMessage(), "error");
            throw new RuntimeException("Failed to upsert document: " + e.getMessage(), e);
        }
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public StatusResponse upload(
            @RestForm("documentName") String documentName,
            @RestForm("rbacTeams") String rbacTeamsCsv,
            @RestForm("file") FileUpload fileUpload) {
        byte[] fileBytes = readMultipartBytes(fileUpload);
        String effectiveDocumentName = (documentName != null && !documentName.isBlank())
                ? documentName
                : (fileUpload != null ? fileUpload.fileName() : null);

        if (effectiveDocumentName == null || effectiveDocumentName.isBlank() || fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("documentName and file are required");
        }

        logService.addLog("Upload document: " + effectiveDocumentName, "upsert");
        List<String> rbacTeams = (rbacTeamsCsv != null && !rbacTeamsCsv.isBlank())
                ? parseRbacTeamsCsv(rbacTeamsCsv)
                : resolveDefaultRbacTeams(effectiveDocumentName);

        try {
            documentService.upsertDocumentFile(effectiveDocumentName, fileBytes, rbacTeams);
            logService.addLog("Successfully uploaded document: " + effectiveDocumentName, "upsert");
            return new StatusResponse("OK");
        } catch (Exception e) {
            logService.addLog("Error uploading document " + effectiveDocumentName + ": " + e.getMessage(), "error");
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @DELETE
    @Path("/{documentName}")
    public StatusResponse deleteByName(@PathParam("documentName") String documentName) {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("documentName is required");
        }
        logService.addLog("Delete document: " + documentName, "delete");
        documentService.deleteDocument(documentName);
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
    public Map<String, String> getDocumentContent(@PathParam("documentName") String documentName) {
        try {
            String content = documentService.readDocumentContent(documentName);
            return Map.of("content", content);
        } catch (NoSuchFileException e) {
            logService.addLog("Document content not found: " + documentName, "warn");
            throw new NotFoundException("Document not found: " + documentName);
        } catch (IllegalArgumentException e) {
            logService.addLog("Invalid documentName for content fetch: " + documentName, "warn");
            throw new BadRequestException("Invalid documentName");
        } catch (Exception e) {
            logService.addLog("Error reading document content " + documentName + ": " + e.getMessage(), "error");
            throw new InternalServerErrorException("Error reading document: " + e.getMessage());
        }
    }

    @GET
    @Path("/file/{documentName}")
    public Response getDocumentFile(@PathParam("documentName") String documentName) {
        return buildDocumentDownloadResponse(documentName);
    }

    @GET
    @Path("/download/{documentName}")
    public Response downloadDocument(@PathParam("documentName") String documentName) {
        return buildDocumentDownloadResponse(documentName);
    }

    private Response buildDocumentDownloadResponse(String documentName) {
        try {
            byte[] fileBytes = documentService.readDocumentFile(documentName);
            String contentType = documentService.detectContentType(documentName);
            return Response.ok(fileBytes, contentType)
                    .header("Content-Disposition", "attachment; filename=\"" + documentName + "\"")
                    .build();
        } catch (NoSuchFileException e) {
            logService.addLog("Document download not found: " + documentName, "warn");
            throw new NotFoundException("Document not found: " + documentName);
        } catch (IllegalArgumentException e) {
            logService.addLog("Invalid documentName for download: " + documentName, "warn");
            throw new BadRequestException("Invalid documentName");
        } catch (Exception e) {
            logService.addLog("Error reading document file " + documentName + ": " + e.getMessage(), "error");
            throw new InternalServerErrorException("Error reading document file: " + e.getMessage());
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

    private List<String> parseRbacTeamsCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private List<String> resolveDefaultRbacTeams(String documentName) {
        return documentService.documentExists(documentName)
                ? accessPolicyService.getAccessTeams(documentName)
                : List.of();
    }

    private byte[] readMultipartBytes(FileUpload fileUpload) {
        if (fileUpload == null || fileUpload.uploadedFile() == null) {
            return null;
        }
        try {
            return Files.readAllBytes(fileUpload.uploadedFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file bytes", e);
        }
    }
}
