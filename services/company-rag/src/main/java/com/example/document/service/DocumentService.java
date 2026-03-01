package com.example.document.service;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import com.example.document.config.VectorDatabaseConfig;
import com.example.document.dto.DocumentsResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class DocumentService {

    private static final Logger LOGGER = Logger.getLogger(DocumentService.class.getName());

    @Inject
    DataSource dataSource;

    @Inject
    VectorDatabaseConfig vectorDatabaseConfig;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    DocumentChunkingService chunkingService;

    @Inject
    DocumentAccessPolicyService accessPolicyService;

    @Inject
    LogService logService;

    @ConfigProperty(name = "demo.dir.location")
    String documentsDir;

    @ConfigProperty(name = "demo.config.split.location")
    String splitConfigLocation;

    @ConfigProperty(name = "document.chunking.default.strategy")
    String defaultStrategy;

    @ConfigProperty(name = "document.preprocessing.mode", defaultValue = "pure-text")
    String preprocessingMode;

    @ConfigProperty(name = "document.preprocessing.docling.base-url", defaultValue = "http://localhost:8086")
    String doclingBaseUrl;

    private EmbeddingStore<TextSegment> embeddingStore;

    private String embeddingTable;

    private String metadataColumn;

    private  Map<String, Object> splitConfig;

    private DoclingServeApi docling;


    @PostConstruct
    void init() {
        embeddingStore = vectorDatabaseConfig.getEmbeddingStore();
        embeddingTable = vectorDatabaseConfig.getEmbeddingTable();
        metadataColumn = vectorDatabaseConfig.getMetadataColumn();

        try {
            List<Path> configLocation = scan(splitConfigLocation);
            InputStream configStream = Files.newInputStream(configLocation.getFirst());
            splitConfig = new Yaml().load(configStream);
        } catch (URISyntaxException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error loading split configuration: ", e);
        }

        ensureDocumentsDirectoryExists();

    }

    private List<Path> scan(String directory) throws URISyntaxException {
        Path dirPath;
        if (directory.startsWith("classpath:/")) {
            String resourceDir = directory.substring("classpath:/".length());
            URL url = Thread.currentThread().getContextClassLoader().getResource(resourceDir);
            dirPath = Paths.get(Objects.requireNonNull(url).toURI());
        } else {
            dirPath = Path.of(directory);
        }

        try (Stream<Path> files = Files.walk(dirPath)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error inspect directory path: ", e);
        }
        return List.of(dirPath);
    }

    public void embedLocalDocuments() {
        try {
            for (Path pathTrail : scanDocumentsDirectory()) {
                if (Files.exists(pathTrail)) {
                    try {
                        embedLoadedDocument(pathTrail);
                        String message = "Loaded and embedded document: " + pathTrail.getFileName();
                        LOGGER.info(message);
                        addActivityLog(message, "ingest");
                    } catch (SkipDocumentException e) {
                        LOGGER.warning(e.getMessage());
                        addActivityLog(e.getMessage(), "warn");
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error loading document " + pathTrail + ": ", e);
                        addActivityLog("Error loading document " + pathTrail.getFileName() + ": " + e.getMessage(), "error");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading documents: ", e);
            addActivityLog("Error scanning documents directory: " + e.getMessage(), "error");
        }
    }
    public void wipeAllEmbeddings() {
        String truncate = "TRUNCATE TABLE " + embeddingTable;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(truncate)) {
             ps.execute();
        } catch (Exception truncateFailed) {
            String delete = "DELETE FROM " + embeddingTable;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(delete)) {
                 int deleted = ps.executeUpdate();
                 LOGGER.info("Wiped embeddings table '" + embeddingTable + "'. Rows deleted: " + deleted);

            } catch (Exception deleteFailed) {
                LOGGER.log(Level.SEVERE, "Error wiping embeddings: ", deleteFailed);
            }
        }
    }


    public void embedLoadedDocument(Path path) throws Exception {
        String content = extractContentForChunking(path);
        chunkAndEmbed(path.getFileName().toString(), content);
    }

    private void chunkAndEmbed(String documentName, String content) {
        Document document = Document.from(content);

        String strategy = matchChunkingStrategy(documentName);
        List<TextSegment> segments = chunkingService.chunkDocument(document, documentName, strategy);

        // Re-embedding should replace vectors but keep document RBAC policy.
        deleteDocumentEmbeddings(documentName, false);

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }
    }

    String extractContentForChunking(Path path) throws IOException {
        if (!preprocessingMode.equals("docling")) {
            if (isPlainTextFile(path)) {
                return Files.readString(path);
            }
            throw new SkipDocumentException(
                    "Skipped " + path.getFileName() + " (non-text file in pure-text mode).",
                    null
            );
        }

        if (!isDoclingCandidate(path)) {
            throw new SkipDocumentException(
                    "Skipped " + path.getFileName() + " (unsupported extension for docling mode).",
                    null
            );
        }

        String base64Source = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                .source(FileSource.builder()
                        .base64String(base64Source)
                        .filename(path.getFileName().toString())
                        .build())
                .build();

        try {
            ConvertDocumentResponse response = getDocling().convertSource(request);
            if (response.getDocument() == null || response.getDocument().getMarkdownContent() == null) {
                throw new IOException("Docling returned no markdown content for " + path);
            }
            return response.getDocument().getMarkdownContent();
        } catch (RuntimeException | IOException e) {
            String fileName = path.getFileName().toString();
            if (isTxtFile(path)) {
                String message = "Docling failed for " + fileName + ", falling back to plain text";
                LOGGER.warning(message);
                addActivityLog(message, "warn");
                return Files.readString(path);
            }

            throw new SkipDocumentException(
                    "Docling failed for " + fileName + "; skipped (non-txt file). Reason: " + e.getMessage(),
                    e
            );
        }
    }

    private DoclingServeApi getDocling() {
        if (docling == null) {
            docling = DoclingServeApi.builder()
                    .baseUrl(doclingBaseUrl)
                    .build();
        }
        return docling;
    }

    private boolean isDoclingFriendlyExtension(Path path) {
        return hasExtension(path, ".pdf")
                || hasExtension(path, ".docx")
                || hasExtension(path, ".pptx")
                || hasExtension(path, ".xlsx");
    }

    private boolean isDoclingCandidate(Path path) {
        return isDoclingFriendlyExtension(path) || isTxtFile(path);
    }

    private boolean isTxtFile(Path path) {
        return hasExtension(path, ".txt");
    }

    private boolean isMdFile(Path path) {
        return hasExtension(path, ".md");
    }

    private boolean isPlainTextFile(Path path) {
        return isTxtFile(path) || isMdFile(path);
    }

    private void addActivityLog(String message, String type) {
        if (logService != null) {
            logService.addLog(message, type);
        }
    }

    static class SkipDocumentException extends IOException {
        SkipDocumentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private List<Path> scanDocumentsDirectory() throws IOException {
        Path dirPath = getDocumentsDirectoryPath();
        if (!Files.exists(dirPath)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dirPath)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private void ensureDocumentsDirectoryExists() {
        try {
            Files.createDirectories(getDocumentsDirectoryPath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create documents directory: " + documentsDir, e);
        }
    }

    private Path getDocumentsDirectoryPath() throws IOException {
        if (documentsDir.startsWith("classpath:/")) {
            throw new IOException("demo.dir.location must be a filesystem path, not classpath.");
        }
        try {
            return Path.of(documentsDir).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IOException("Invalid document storage path: " + documentsDir, e);
        }
    }

    private Path resolveDocumentPath(String documentName) throws IOException {
        String safeName = sanitizeDocumentName(documentName);
        Path base = getDocumentsDirectoryPath();
        Path target = base.resolve(safeName).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("Invalid document name/path traversal attempt: " + documentName);
        }
        return target;
    }

    private String sanitizeDocumentName(String documentName) {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("documentName is required");
        }
        String trimmed = documentName.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Invalid documentName: path separators are not allowed");
        }
        return trimmed;
    }

    private boolean hasExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        int nameLength = fileName.length();
        int extLength = extension.length();
        return nameLength >= extLength
                && fileName.regionMatches(true, nameLength - extLength, extension, 0, extLength);
    }

    public void deleteDocumentEmbeddings(String documentName) {
        deleteDocumentEmbeddings(documentName, true);
    }

    public void deleteDocument(String documentName) {
        deleteDocumentFromStorage(documentName);
        deleteDocumentEmbeddings(documentName, true);
    }

    public void deleteDocumentEmbeddings(String documentName, boolean removeAccessPolicy) {
        try {
            embeddingStore.removeAll(metadataKey("documentName").isEqualTo(documentName));
            if (removeAccessPolicy) {
                accessPolicyService.removeDocument(documentName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Warning: Error deleting embeddings for " + documentName + ": ", e);
        }
    }

    private String matchChunkingStrategy(String documentName) {
        return Optional.ofNullable(splitConfig)
                .map(c -> c.get("documents"))
                .map(m -> (Map<String, Object>) m)
                .map(docs -> docs.get(documentName))
                .filter(Map.class::isInstance)
                .map(m -> (Map<String, Object>) m)
                .map(doc -> doc.get("strategy"))
                .map(Object::toString)
                .orElse(defaultStrategy);
    }

    public void upsertDocumentFile(String documentName, byte[] content, List<String> rbacTeams) {
        Path path = saveDocumentToStorage(documentName, content);
        accessPolicyService.updateDocumentAccess(documentName, rbacTeams);
        try {
            embedLoadedDocument(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to embed uploaded document '" + documentName + "'", e);
        }
    }

    public String readDocumentContent(String documentName) throws IOException {
        Path path = resolveDocumentPath(documentName);
        return Files.readString(path);
    }

    public boolean documentExists(String documentName) {
        try {
            return Files.exists(resolveDocumentPath(documentName));
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    public byte[] readDocumentFile(String documentName) throws IOException {
        Path path = resolveDocumentPath(documentName);
        return Files.readAllBytes(path);
    }

    public String detectContentType(String documentName) throws IOException {
        Path path = resolveDocumentPath(documentName);
        String detected = Files.probeContentType(path);
        return detected != null ? detected : "application/octet-stream";
    }

    private Path saveDocumentToStorage(String documentName, byte[] content) {
        try {
            ensureDocumentsDirectoryExists();
            Path path = resolveDocumentPath(documentName);
            Files.write(path, content);
            return path;
        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException("Failed to save document '" + documentName + "' to storage", e);
        }
    }

    private void deleteDocumentFromStorage(String documentName) {
        try {
            Path path = resolveDocumentPath(documentName);
            Files.deleteIfExists(path);
        } catch (IOException | IllegalArgumentException e) {
            throw new RuntimeException("Failed to delete document '" + documentName + "' from storage", e);
        }
    }


    public List<DocumentsResponse.DocumentInfo> findAllDocuments() {
        Set<String> documentNames = new HashSet<>(accessPolicyService.getAllAccessPolicies().keySet());

        String sql = """
                SELECT DISTINCT JSON_VALUE(%s, '$.documentName')
                FROM %s
                WHERE JSON_VALUE(%s, '$.documentName') IS NOT NULL
                """.formatted(metadataColumn, embeddingTable, metadataColumn);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !name.isBlank()) {
                    documentNames.add(name);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting document names from Oracle embeddings table: ", e);
        }

        List<DocumentsResponse.DocumentInfo> documents = documentNames.stream()
                .map(name -> {
                    List<String> teams = accessPolicyService.getAccessTeams(name);
                    String link = "/documents/" + name;
                    return new DocumentsResponse.DocumentInfo(name, link, teams);
                })
                .collect(toList());

        return documents;
    }
}
