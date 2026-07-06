package com.example.ticket.service;

import com.example.ticket.model.TicketData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.logging.Logger;
import java.util.stream.Stream;

@Service.Singleton
public class DemoDataService {

    private static final Logger log = Logger.getLogger(LogService.LOGGER_NAME);
    private static final int PROGRESS_INTERVAL = 10;
    private static final String DEFAULT_DEMO_DATA_DIRECTORY = "demo-data";
    private static final String DEMO_DATA_DIRECTORY_CONFIG = "similarity.tickets.demo-data.directory";

    VectorService vectorService;

    EmbeddingService embeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String demoDataDirectory;

    @Service.Inject
    public DemoDataService(VectorService vectorService, EmbeddingService embeddingService, Config config) {
        this(vectorService, embeddingService, demoDataDirectory(config));
    }

    DemoDataService(VectorService vectorService, EmbeddingService embeddingService) {
        this(vectorService, embeddingService, DEFAULT_DEMO_DATA_DIRECTORY);
    }

    DemoDataService(VectorService vectorService, EmbeddingService embeddingService, String demoDataDirectory) {
        this.vectorService = vectorService;
        this.embeddingService = embeddingService;
        this.demoDataDirectory = normalizeDirectory(demoDataDirectory);
    }

    public CompletionStage<Void> loadDemoDataAsync() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        return CompletableFuture.runAsync(this::loadDemoData, executor)
                .whenComplete((_, _) -> executor.close());
    }

    public void loadDemoData() {
        log.info("Starting demo data load...");
        vectorService.deleteAllTickets();
        log.info("Cleared existing Oracle AI Database data");

        List<String> files = demoDataFiles();
        log.info("Found " + files.size() + " demo data files");

        List<Map<String, Object>> allTickets = new ArrayList<>();
        for (String file : files) {
            List<Map<String, Object>> tickets = loadTicketsFromFile(file);
            allTickets.addAll(tickets.stream()
                    .filter(t -> t.get("ticketType") != null && t.get("originalRequest") != null)
                    .toList());
        }
        int totalToLoad = allTickets.size();
        log.info("Found " + totalToLoad + " demo tickets to embed");

        for (int i = 0; i < allTickets.size(); i++) {
            Map<String, Object> ticket = allTickets.get(i);
            Long ticketId = Long.parseLong(ticket.get("id").toString());
            String ticketType = ticket.get("ticketType").toString();
            String originalRequest = ticket.get("originalRequest").toString();

            float[] embedding = embeddingService.embed(originalRequest);
            vectorService.upsertTicket(new TicketData(ticketId, ticketType, originalRequest, embedding, System.currentTimeMillis()));

            int processed = i + 1;
            if (processed % PROGRESS_INTERVAL == 0) {
                String msg = "Processed tickets " + processed + "/" + totalToLoad;
                log.info(msg);
            }
        }
        log.info("Demo data load complete: " + totalToLoad + " tickets loaded");
    }

    private List<String> demoDataFiles() {
        Path filesystemDirectory = Path.of(demoDataDirectory);
        if (Files.isDirectory(filesystemDirectory)) {
            return filesystemDemoDataFiles(filesystemDirectory);
        }
        return classpathDemoDataFiles(demoDataDirectory);
    }

    private List<String> filesystemDemoDataFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list demo data directory: " + directory, e);
        }
    }

    private List<String> classpathDemoDataFiles(String directory) {
        var resource = Thread.currentThread().getContextClassLoader().getResource(directory);
        if (resource == null) {
            throw new RuntimeException("Demo data directory not found: " + directory);
        }
        if ("jar".equals(resource.getProtocol())) {
            return jarDemoDataFiles(directory, resource);
        }
        if (!"file".equals(resource.getProtocol())) {
            throw new RuntimeException("Unsupported demo data directory protocol " + resource.getProtocol() + ": " + directory);
        }
        try {
            return filesystemDemoDataFiles(Path.of(resource.toURI()))
                    .stream()
                    .map(path -> directory + "/" + Path.of(path).getFileName())
                    .toList();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid demo data directory URI: " + directory, e);
        }
    }

    private List<String> jarDemoDataFiles(String directory, java.net.URL resource) {
        try {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            String prefix = normalizeDirectory(connection.getEntryName()) + "/";
            try (var jar = connection.getJarFile()) {
                return jar.stream()
                        .map(JarEntry::getName)
                        .filter(name -> name.startsWith(prefix))
                        .filter(name -> name.endsWith(".json"))
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list demo data directory from jar: " + directory, e);
        }
    }

    private List<Map<String, Object>> loadTicketsFromFile(String filePath) {

        try (InputStream inputStream = openDemoDataFile(filePath)) {
            List<Map<String, Object>> tickets = objectMapper.readValue(
                    inputStream,
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionType(List.class, Map.class)
            );

            return tickets != null ? tickets : List.of();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load demo data from " + filePath, e);
        }
    }

    private InputStream openDemoDataFile(String filePath) throws IOException {
        Path filesystemFile = Path.of(filePath);
        if (Files.isRegularFile(filesystemFile)) {
            return Files.newInputStream(filesystemFile);
        }

        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(filePath);

        if (Objects.isNull(inputStream)) {
            throw new RuntimeException("Demo data file not found: " + filePath);
        }
        return inputStream;
    }

    private static String demoDataDirectory(Config config) {
        return config.get(DEMO_DATA_DIRECTORY_CONFIG)
                .asString()
                .orElse(DEFAULT_DEMO_DATA_DIRECTORY);
    }

    private static String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return DEFAULT_DEMO_DATA_DIRECTORY;
        }
        String normalizedDirectory = directory.strip();
        while (normalizedDirectory.endsWith("/") && normalizedDirectory.length() > 1) {
            normalizedDirectory = normalizedDirectory.substring(0, normalizedDirectory.length() - 1);
        }
        return normalizedDirectory;
    }
}
