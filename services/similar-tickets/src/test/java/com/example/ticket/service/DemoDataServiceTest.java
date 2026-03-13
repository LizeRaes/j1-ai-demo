package com.example.ticket.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataServiceTest {

    @Test
    void testLoadDemoDataSuccess() {
        LogService logHandler = new LogService();
        logHandler.register();
        try {
            RecordingVectorService vectorService = new RecordingVectorService();
            RecordingEmbeddingService embeddingService = new RecordingEmbeddingService();
            DemoDataService demoDataService = new DemoDataService(vectorService, embeddingService);

            demoDataService.loadDemoData();

            List<LogService.LogEntry> logs = logHandler.getLogs();
            int expectedLogCount = 3 + (vectorService.upsertCount / 10);

            assertEquals(1, vectorService.deleteAllCalls);
            assertEquals(vectorService.upsertCount, embeddingService.embedCount);
            assertEquals(expectedLogCount, logs.size());
            assertTrue(vectorService.upsertCount > 0);
            assertTrue(logs.stream().allMatch(log -> "loadDemoData".equals(log.type())));
            assertEquals("[INFO] Starting demo data load...", logs.getFirst().message());
            assertEquals("[INFO] Cleared existing Oracle AI Database data", logs.get(1).message());
            assertEquals("[INFO] Demo data load complete: " + vectorService.upsertCount + " tickets loaded",
                         logs.getLast().message());
        } finally {
            logHandler.unregister();
        }
    }

    @Test
    void testLoadDemoDataFails() {
        LogService logHandler = new LogService();
        logHandler.register();
        try {
            RuntimeException failure = new RuntimeException("Failed to delete all tickets");
            RecordingVectorService vectorService = new RecordingVectorService(failure);
            RecordingEmbeddingService embeddingService = new RecordingEmbeddingService();
            DemoDataService demoDataService = new DemoDataService(vectorService, embeddingService);

            RuntimeException exception = assertThrows(RuntimeException.class, demoDataService::loadDemoData);
            List<LogService.LogEntry> logs = logHandler.getLogs();

            assertTrue(exception.getMessage().contains("Failed to delete all tickets"));
            assertEquals(1, vectorService.deleteAllCalls);
            assertEquals(0, vectorService.upsertCount);
            assertEquals(1, logs.size());
            assertEquals("loadDemoData", logs.getFirst().type());
            assertEquals("[INFO] Starting demo data load...", logs.getFirst().message());
        } finally {
            logHandler.unregister();
        }
    }

    private static final class RecordingEmbeddingService extends EmbeddingService {
        private int embedCount;

        private RecordingEmbeddingService() {
            super((EmbeddingModel) null);
        }

        @Override
        public float[] embed(String text) {
            embedCount++;
            return new float[]{1.0f, 2.0f};
        }
    }

    private static final class RecordingVectorService extends VectorService {
        private int deleteAllCalls;
        private int upsertCount;
        private final RuntimeException deleteAllFailure;

        private RecordingVectorService() {
            this(null);
        }

        private RecordingVectorService(RuntimeException deleteAllFailure) {
            super(dummyEmbeddingStore(), dummyEmbeddingModel(), dummyConfig(), dummyDataSource());
            this.deleteAllFailure = deleteAllFailure;
        }

        @Override
        public void deleteAllTickets() {
            deleteAllCalls++;
            if (deleteAllFailure != null) {
                throw deleteAllFailure;
            }
        }

        @Override
        public void upsertTicket(Long ticketId, String ticketType, String text, float[] vector) {
            upsertCount++;
        }

        @SuppressWarnings("unchecked")
        private static EmbeddingStore<TextSegment> dummyEmbeddingStore() {
            return (EmbeddingStore<TextSegment>) Proxy.newProxyInstance(
                    DemoDataServiceTest.class.getClassLoader(),
                    new Class<?>[]{EmbeddingStore.class},
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private static EmbeddingModel dummyEmbeddingModel() {
            return (EmbeddingModel) Proxy.newProxyInstance(
                    DemoDataServiceTest.class.getClassLoader(),
                    new Class<?>[]{EmbeddingModel.class},
                    (proxy, method, args) -> {
                        if ("embedAll".equals(method.getName())) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        if ("dimension".equals(method.getName())) {
                            return 2;
                        }
                        if ("modelName".equals(method.getName())) {
                            return "test-model";
                        }
                        if ("embed".equals(method.getName())) {
                            return null;
                        }
                        return proxy;
                    });
        }

        private static Config dummyConfig() {
            return Config.create(() -> MapConfigSource.create(Map.of(
                    "langchain4j.embedding-stores.oracle-embedding-store.embedding-table.name", "TEST_TABLE",
                    "langchain4j.embedding-stores.oracle-embedding-store.embedding-table.embedding-column", "EMBEDDING",
                    "langchain4j.embedding-stores.oracle-embedding-store.embedding-table.metadata-column", "METADATA",
                    "langchain4j.embedding-stores.oracle-embedding-store.embedding-table.text-column", "TEXT"
            )));
        }

        private static DataSource dummyDataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    DemoDataServiceTest.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
