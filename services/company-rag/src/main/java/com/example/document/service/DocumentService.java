package com.example.document.service;

import com.example.document.config.VectorDatabaseConfig;
import com.example.document.dto.DocumentsResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.io.IOException;
import java.io.InputStream;
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

    @ConfigProperty(name = "demo.dir.location")
    String documentsDir;

    @ConfigProperty(name = "demo.config.split.location")
    String splitConfigLocation;

    @ConfigProperty(name = "document.chunking.default.strategy")
    String defaultStrategy;

    private EmbeddingStore<TextSegment> embeddingStore;

    private String embeddingTable;

    private String metadataColumn;

    private  Map<String, Object> splitConfig;


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
            for (Path pathTrail : scan(documentsDir)) {
                if (Files.exists(pathTrail)) {
                    embedLoadedDocument(pathTrail);
                    LOGGER.info("Loaded and embedded document: " + pathTrail);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading documents: ", e);
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
        try (InputStream docStream = Files.newInputStream(path)) {
            String content = new String(docStream.readAllBytes());
            chunkAndEmbed(path.getFileName().toString(), content);
        }
    }

    private void chunkAndEmbed(String documentName, String content) {
        Document document = Document.from(content);

        String strategy = matchChunkingStrategy(documentName);
        List<TextSegment> segments = chunkingService.chunkDocument(document, documentName, strategy);

        deleteDocumentEmbeddings(documentName);

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }
    }

    public void deleteDocumentEmbeddings(String documentName) {
        try {
            embeddingStore.removeAll(metadataKey("documentName").isEqualTo(documentName));
            accessPolicyService.removeDocument(documentName);
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

    public void upsertDocument(String documentName, String content, List<String> rbacTeams) {
        saveDocumentToResources(documentName, content);
        accessPolicyService.updateDocumentAccess(documentName, rbacTeams);
        chunkAndEmbed(documentName, content);
    }

    private void saveDocumentToResources(String documentName, String content) {
        // TODO In a production system, you'd write to a writable directory
        // For now, we'll just keep it in memory or use a different location
        // This is a limitation of packaging static in JAR files
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
