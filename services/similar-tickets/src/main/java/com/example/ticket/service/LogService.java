package com.example.ticket.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.service.registry.Service;

@Service.Singleton
@Service.RunLevel(Service.RunLevel.STARTUP)
public class LogService extends Handler {
    public static final String LOGGER_NAME = "ticket-service-web";
    private static final Logger WEB_LOGGER = Logger.getLogger(LOGGER_NAME);

    private final Queue<LogEntry> logs = new ConcurrentLinkedQueue<>();
    private static final int MAX_LOGS = 1000;

    @Service.Inject
    LogService() {
        setLevel(Level.ALL);
    }

    @Service.PostConstruct
    void register() {
        WEB_LOGGER.setLevel(Level.ALL);
        WEB_LOGGER.addHandler(this);
    }

    @Service.PreDestroy
    void unregister() {
        WEB_LOGGER.removeHandler(this);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) {
            return;
        }

        logs.offer(new LogEntry(formatMessage(record), resolveType(record), record.getMillis()));
        while (logs.size() > MAX_LOGS) {
            logs.poll();
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        unregister();
    }

    public List<LogEntry> getLogs() {
        return new ArrayList<>(logs);
    }

    private String formatMessage(LogRecord record) {
        String message = record.getMessage();
        if (message == null || message.isBlank()) {
            message = record.getThrown() != null ? record.getThrown().toString() : "";
        } else if (record.getThrown() != null) {
            message = message + ": " + record.getThrown();
        }
        return "[" + record.getLevel().getName() + "] " + message;
    }

    private String resolveType(LogRecord record) {
        String methodName = record.getSourceMethodName();
        if (methodName != null && !methodName.isBlank()) {
            return methodName;
        }

        String loggerName = record.getLoggerName();
        if (loggerName != null && !loggerName.isBlank()) {
            return loggerName;
        }

        return "web";
    }

    public record LogEntry(String message, String type, long timestamp) { }
}
