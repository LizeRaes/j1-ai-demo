package com.example.document.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@ApplicationScoped
public class DocumentService {

    @Inject
    EmbeddingService embeddingService;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    DocumentChunkingService chunkingService;

    @Inject
    DocumentAccessPolicyService accessPolicyService;

    @Inject
    com.example.document.config.QdrantConfig qdrantConfig;

    @Inject
    io.qdrant.client.QdrantClient qdrantClient;

    @ConfigProperty(name = "document.chunking.default.strategy", defaultValue = "recursive")
    String defaultStrategy;

    @ConfigProperty(name = "embedding.dimension", defaultValue = "3072")
    int embeddingDimension;

    private static final String DOCUMENTS_DIR = "documents";
    private static final String CONFIG_DIR = "config";
    private volatile boolean collectionChecked = false;

    /**
     * Loads and embeds all documents from the static/documents directory on startup.
     */
    public void loadAndEmbedAllDocuments() {
        try {
            // Known document names - in a real system, you might read this from a config file
            // or scan the directory. For now, we'll try to load common document names.
            String[] knownDocuments = {
                "Approved_Response_Templates.txt",
                "Billing_Refund_Policy.txt",
                "Data_Privacy_User_Data_Handling.txt",
                "Known_Bugs_Limitations.txt",
                "MedicalAppointment_Architecture.txt",
                "Payment_System_Payment_Flow.txt",
                "Security_Escalation_Policy.txt"
            };

            // Try to load each known document
            for (String fileName : knownDocuments) {
                try {
                    InputStream docStream = getClass().getClassLoader()
                        .getResourceAsStream(DOCUMENTS_DIR + "/" + fileName);
                    if (docStream != null) {
                        docStream.close();
                        loadAndEmbedDocument(fileName);
                        System.out.println("Loaded and embedded document: " + fileName);
                    }
                } catch (Exception e) {
                    // Document doesn't exist or error loading - skip it
                    System.out.println("Skipping document " + fileName + " (not found or error: " + e.getMessage() + ")");
                }
            }

            // Also try to list files if in file system mode (development)
            try {
                java.net.URL documentsUrl = getClass().getClassLoader().getResource(DOCUMENTS_DIR);
                if (documentsUrl != null && "file".equals(documentsUrl.getProtocol())) {
                    Path documentsPath = Paths.get(documentsUrl.toURI());
                    if (Files.isDirectory(documentsPath)) {
                        Files.list(documentsPath)
                            .filter(path -> path.toString().endsWith(".txt"))
                            .forEach(path -> {
                                String fileName = path.getFileName().toString();
                                // Only load if not already loaded
                                boolean alreadyLoaded = false;
                                for (String known : knownDocuments) {
                                    if (known.equals(fileName)) {
                                        alreadyLoaded = true;
                                        break;
                                    }
                                }
                                if (!alreadyLoaded) {
                                    try {
                                        loadAndEmbedDocument(fileName);
                                        System.out.println("Loaded and embedded document: " + fileName);
                                    } catch (Exception e) {
                                        System.err.println("Error loading document " + fileName + ": " + e.getMessage());
                                    }
                                }
                            });
                    }
                }
            } catch (Exception e) {
                // Ignore - we already tried known documents
            }

        } catch (Exception e) {
            System.err.println("Error loading documents: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Wipes all embeddings from the database.
     */
    public void wipeAllEmbeddings() {
        try {
            System.out.println("Wiping all embeddings from collection...");
            // Remove all embeddings by using a filter that matches everything
            // Since we can't easily delete all, we'll delete by document name for each known document
            // and then try to clear the collection if possible
            String collectionName = qdrantConfig.getCollectionName();

            // Try to delete the collection and recreate it
            try {
                qdrantClient.deleteCollectionAsync(collectionName).get();
                System.out.println("Deleted collection: " + collectionName);
            } catch (Exception e) {
                // Collection might not exist, that's fine
                if (e.getMessage() != null && !e.getMessage().contains("doesn't exist")) {
                    System.err.println("Error deleting collection: " + e.getMessage());
                }
            }

            // Reset collection checked flag so it will be recreated
            collectionChecked = false;
            ensureCollectionExists();
            System.out.println("Database wiped successfully.");
        } catch (Exception e) {
            System.err.println("Error wiping database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensures the Qdrant collection exists, creating it if necessary.
     * Made public so StartupService can call it.
     */
    public void ensureCollectionExists() {
        if (collectionChecked) {
            return;
        }
        synchronized (this) {
            if (collectionChecked) {
                return;
            }
            try {
                String collectionName = qdrantConfig.getCollectionName();
                // Check if collection exists
                boolean collectionExists = false;
                try {
                    qdrantClient.getCollectionInfoAsync(collectionName).get();
                    // Collection exists
                    collectionExists = true;
                } catch (java.util.concurrent.ExecutionException e) {
                    // Check if the cause is a NOT_FOUND error (collection doesn't exist)
                    Throwable cause = e.getCause();
                    if (cause instanceof io.grpc.StatusRuntimeException) {
                        io.grpc.StatusRuntimeException grpcException = (io.grpc.StatusRuntimeException) cause;
                        if (grpcException.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                            // Collection doesn't exist - this is expected on first startup
                            collectionExists = false;
                        } else {
                            // Some other error
                            throw e;
                        }
                    } else {
                        // Some other error
                        throw e;
                    }
                }

                if (!collectionExists) {
                    // Collection doesn't exist, create it
                    // Use configured embedding dimension (no OpenAI call needed)
                    int dimension = embeddingDimension;

                    // Create collection
                    qdrantClient.createCollectionAsync(
                        collectionName,
                        io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                            .setSize(dimension)
                            .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                            .build()
                    ).get();
                    System.out.println("Created Qdrant collection: " + collectionName + " with dimension " + dimension);
                }
                collectionChecked = true;
            } catch (Exception e) {
                System.err.println("Error ensuring collection exists: " + e.getMessage());
                e.printStackTrace();
                // Don't set collectionChecked = true so we'll try again
            }
        }
    }

    /**
     * Loads and embeds a single document.
     */
    public void loadAndEmbedDocument(String documentName) throws Exception {
        // Ensure collection exists before proceeding
        ensureCollectionExists();

        // Read document from static
        InputStream docStream = getClass().getClassLoader()
            .getResourceAsStream(DOCUMENTS_DIR + "/" + documentName);

        if (docStream == null) {
            throw new FileNotFoundException("Document not found: " + documentName);
        }

        String content = new String(docStream.readAllBytes());
        docStream.close();

        Document document = Document.from(content);

        // Determine chunking strategy
        String strategy = getChunkingStrategy(documentName);

        // Chunk the document
        List<TextSegment> segments = chunkingService.chunkDocument(document, documentName, strategy);

        // Delete existing embeddings for this document
        deleteDocumentEmbeddings(documentName);

        // Embed and store each chunk
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }
    }

    /**
     * Deletes all embeddings for a document.
     * Silently handles the case where the collection doesn't exist yet.
     */
    public void deleteDocumentEmbeddings(String documentName) {
        try {
            embeddingStore.removeAll(
                metadataKey("documentName").isEqualTo(documentName)
            );
        } catch (Exception e) {
            // Collection might not exist yet (first startup) - this is fine, just ignore
            String errorMsg = e.getMessage();
            Throwable cause = e.getCause();
            if (errorMsg != null && errorMsg.contains("doesn't exist")) {
                // Expected on first startup, ignore
                return;
            }
            // Also check cause (for wrapped exceptions like ExecutionException)
            if (cause != null) {
                String causeMsg = cause.getMessage();
                if (causeMsg != null && causeMsg.contains("doesn't exist")) {
                    // Expected on first startup, ignore
                    return;
                }
            }
            // For other errors, log but don't fail
            System.err.println("Warning: Error deleting embeddings for " + documentName + ": " + errorMsg);
        }
    }

    /**
     * Gets the chunking strategy for a document from config, or returns default.
     */
    private String getChunkingStrategy(String documentName) {
        try {
            InputStream configStream = getClass().getClassLoader()
                .getResourceAsStream(CONFIG_DIR + "/document-splitting-config.yaml");

            if (configStream == null) {
                return defaultStrategy;
            }

            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(configStream);
            configStream.close();

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
            System.err.println("Error reading splitting config: " + e.getMessage());
        }

        return defaultStrategy;
    }

    /**
     * Adds or updates a document with its content and RBAC teams.
     */
    public void upsertDocument(String documentName, String content, List<String> rbacTeams) throws Exception {
        // Save document to static/documents
        saveDocumentToResources(documentName, content);

        // Update access policy
        accessPolicyService.updateDocumentAccess(documentName, rbacTeams);

        // Load and embed the document
        Document document = Document.from(content);
        String strategy = getChunkingStrategy(documentName);
        List<TextSegment> segments = chunkingService.chunkDocument(document, documentName, strategy);

        // Delete existing embeddings
        deleteDocumentEmbeddings(documentName);

        // Embed and store
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }
    }

    /**
     * Saves a document to the static/documents directory.
     * Note: In a real application, you'd want to handle this differently as static are typically read-only.
     * For now, we'll just update the in-memory representation.
     */
    private void saveDocumentToResources(String documentName, String content) {
        // In a production system, you'd write to a writable directory
        // For now, we'll just keep it in memory or use a different location
        // This is a limitation of packaging static in JAR files
    }

    /**
     * Gets all document names from both the access policy and from actual loaded documents.
     */
    public List<String> getAllDocumentNames() {
        java.util.Set<String> documentNames = new java.util.HashSet<>();

        // Add documents from access policy
        documentNames.addAll(accessPolicyService.getAllAccessPolicies().keySet());

        // Also get document names from chunks in Qdrant using direct scroll API
        try {
            Points.ScrollResponse response = qdrantClient.scrollAsync(
                    Points.ScrollPoints.newBuilder()
                            .setCollectionName(qdrantConfig.getCollectionName())
                            .setLimit(10_000)
                            .setWithPayload(
                                    Points.WithPayloadSelector.newBuilder().setEnable(true).build()
                            )
                            .setWithVectors(
                                    Points.WithVectorsSelector.newBuilder().setEnable(false).build()
                            )
                            .build()
            ).get();

            response.getResultList().stream()
                    .map(p -> {
                        JsonWithInt.Value docNameValue = p.getPayloadMap().get("documentName");
                        if (docNameValue != null && docNameValue.hasStringValue()) {
                            return docNameValue.getStringValue();
                        }
                        return null;
                    })
                    .filter(name -> name != null)
                    .forEach(documentNames::add);
        } catch (Exception e) {
            System.err.println("Error getting document names from chunks: " + e.getMessage());
        }

        return new ArrayList<>(documentNames);
    }
}
