package com.example.document.config;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;

import javax.sql.DataSource;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class VectorDatabaseConfig {

    @Inject
    DataSource dataSource;

    @Produces
    EmbeddingStore<TextSegment> embeddingStore;

    @ConfigProperty(name = "oracleai.embedding.table.name")
    String embeddingTable;

    @ConfigProperty(name = "oracleai.embedding.metadata.column")
    String metadataColumn;

    void onStart(@Observes @Priority(Interceptor.Priority.APPLICATION - 100) StartupEvent ev) {
        embeddingStore = OracleEmbeddingStore.builder()
                .dataSource(dataSource)
                .embeddingTable(embeddingTable, CreateOption.CREATE_IF_NOT_EXISTS)
                .build();
    }


    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    public String getEmbeddingTable() {
        return embeddingTable;
    }

    public String getMetadataColumn() {return metadataColumn;}
}
