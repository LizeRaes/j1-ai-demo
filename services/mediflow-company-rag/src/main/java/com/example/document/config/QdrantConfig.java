package com.example.document.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QdrantConfig {

    @ConfigProperty(name = "qdrant.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "qdrant.port", defaultValue = "6334")
    int port;

    @ConfigProperty(name = "qdrant.collection.name", defaultValue = "document-embeddings")
    String collectionName;

    // EmbeddingModel is now automatically provided by quarkus-langchain4j-openai extension
    // No need to manually produce it!

    @Produces
    @ApplicationScoped
    public EmbeddingStore<TextSegment> embeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host(host)
                .port(port)
                .useTls(false)
                .collectionName(collectionName)
                .build();
    }

    @Produces
    @ApplicationScoped
    public QdrantClient qdrantClient() {
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, port, false).build();
        return new QdrantClient(grpcClient);
    }

    public String getCollectionName() {
        return collectionName;
    }
}
