package com.example.aicodingassistant.logging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ApplicationScoped
public class JobLogService {
    @ConfigProperty(name = "app.logs-root")
    String logsRoot;

    @ConfigProperty(name = "app.logs.retention-hours")
    long logRetentionHours;

    @Inject
    JobLogStream jobLogStream;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record LogEntry(String timestamp, String jobId, String message) {}

    public Consumer<String> loggerForJob(String jobId) {
        Path logFile = getLogPath(jobId);
        cleanupOldLogs();
        return message -> {
            LocalDateTime now = LocalDateTime.now();
            String timestamp = TS.format(now);
            String line = timestamp + " [" + jobId + "] " + message + System.lineSeparator();
            try {
                Files.createDirectories(logFile.getParent());
                Files.writeString(
                        logFile,
                        line,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Failed to write job log file: " + e.getMessage());
            }
            jobLogStream.append(new LogEntry(timestamp, jobId, message));
        };
    }

    public Path getLogPath(String jobId) {
        return Path.of(logsRoot).toAbsolutePath().normalize().resolve("job-" + jobId + ".log");
    }

    private void cleanupOldLogs() {
        Path root = Path.of(logsRoot).toAbsolutePath().normalize();
        long retentionHours = Math.max(1, logRetentionHours);
        Instant cutoff = Instant.now().minus(Duration.ofHours(retentionHours));
        try {
            Files.createDirectories(root);
            try (Stream<Path> paths = Files.list(root)) {
                for (Path path : paths.toList()) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    if (!path.getFileName().toString().endsWith(".log")) {
                        continue;
                    }
                    Instant modified = Files.getLastModifiedTime(path).toInstant();
                    if (modified.isBefore(cutoff)) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        } catch (IOException ignored) { }
    }
}
