package com.example.document.service;

import com.example.document.config.VectorDatabaseConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.io.FileNotFoundException;
import java.io.InputStream;
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
    @ConfigProperty(name = "demo.dir.location")
    String documentsDir;

    @Inject
    @ConfigProperty(name = "demo.config.location")
    String configDir;

    @ConfigProperty(name="demo.known.documents")
    String[] knownDocuments;

    @ConfigProperty(name = "document.chunking.default.strategy")
    String defaultStrategy;

    private EmbeddingStore<TextSegment> embeddingStore;

    private String embeddingTable;

    private String metadataColumn;


    @PostConstruct
    void init() {
        embeddingStore = vectorDatabaseConfig.getEmbeddingStore();
        embeddingTable = vectorDatabaseConfig.getEmbeddingTable();
        metadataColumn = vectorDatabaseConfig.getMetadataColumn();
    }


    public void embedLoadedDocuments() {
        try {

            // Load known docs from classpath resources
            for (String fileName : knownDocuments) {
                try (InputStream docStream = getClass().getClassLoader()
                        .getResourceAsStream(documentsDir + fileName)) {
                    if (docStream != null) {
                        embedLoadedDocument(fileName);
                        LOGGER.info("Loaded and embedded document: " + fileName);
                    }
                } catch (Exception e) {
                    LOGGER.info("Skipping document " + fileName + " (not found or error: " + e.getMessage() + ")");
                }
            }

            // Dev-mode: list extra .txt files if resources are on filesystem
            try {
                URL documentsUrl = getClass().getClassLoader().getResource(documentsDir);
                if (documentsUrl != null && documentsUrl.getProtocol().equals("file")) {
                    Path documentsPath = Paths.get(documentsUrl.toURI());
                    if (Files.isDirectory(documentsPath)) {
                        Set<String> known = new HashSet<>(Arrays.asList(knownDocuments));
                        try (Stream<Path> fileStream = Files.walk(documentsPath)) {
                            fileStream
                                    .filter(path -> path.toString().endsWith(".txt"))
                                    .map(path -> path.getFileName().toString())
                                    .filter(fileName -> !known.contains(fileName))
                                    .forEach(fileName -> {
                                        try {
                                            embedLoadedDocument(fileName);
                                            LOGGER.info("Loaded and embedded document: " + fileName);
                                        } catch (Exception e) {
                                            LOGGER.log(Level.SEVERE, "Error loading document " + fileName + ": ", e);
                                        }
                                    });
                        }
                    }
                }
            } catch (Exception ignored) {
                LOGGER.log(Level.INFO, "Ignore document directory " + documentsDir, ignored);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading documents: ", e);
        }
    }

    public void wipeAllEmbeddings() {
        String truncate = "TRUNCATE TABLE " + embeddingTable.toUpperCase();

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(truncate)) {
            ps.execute();
        } catch (Exception truncateFailed) {
            String delete = "DELETE FROM " + embeddingTable.toUpperCase();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(delete)) {
                int deleted = ps.executeUpdate();
                LOGGER.info("Wiped embeddings table '" + embeddingTable + "'. Rows deleted: " + deleted);

            } catch (Exception deleteFailed) {
                LOGGER.log(Level.SEVERE, "Error wiping embeddings: ", deleteFailed);
            }
        }
    }


    public void embedLoadedDocument(String documentName) throws Exception {
        try (InputStream docStream = getClass().getClassLoader()
                .getResourceAsStream(documentsDir + documentName)) {

            if (docStream == null) {
                throw new FileNotFoundException("Document not found: " + documentName);
            }

            String content = new String(docStream.readAllBytes());
            chunkAndEmbed(documentName, content);
        }
    }

    private void chunkAndEmbed(String documentName, String content) {
        Document document = Document.from(content);

        String strategy = getChunkingStrategy(documentName);
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
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Warning: Error deleting embeddings for " + documentName + ": ", e);
        }
    }

    private String getChunkingStrategy(String documentName) {
        try (InputStream configStream = getClass().getClassLoader()
                .getResourceAsStream(configDir + "document-splitting-config.yaml")) {

            if (configStream == null) {
                return defaultStrategy;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(configStream);

            if (config != null && config.containsKey("documents")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> documents = (Map<String, Object>) config.get("documents");

                if (documents.containsKey(documentName)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docConfig = (Map<String, Object>) documents.get(documentName);
                    Object strategy = docConfig.get("strategy");
                    if (strategy != null) {
                        return strategy.toString();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error reading splitting config: ", e);
        }
        return defaultStrategy;
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

    /**
     * Gets all document names from access policy plus what’s actually stored in Oracle.
     * We query DISTINCT documentName from metadata JSON using JSON_VALUE.
     * Adjust JSON path if your metadata structure differs.
     */
    public List<String> getAllDocumentNames() {
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

        return new ArrayList<>(documentNames);
    }
}
