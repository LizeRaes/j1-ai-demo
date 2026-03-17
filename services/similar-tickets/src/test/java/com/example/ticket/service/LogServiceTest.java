package com.example.ticket.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogServiceTest {

    @Test
    void testPublishAddsLog() {
        LogService logHandler = new LogService();

        LogRecord record = logRecord(Level.INFO, "Test message", "testMethod", 123L);
        logHandler.publish(record);

        List<LogService.LogEntry> logs = logHandler.getLogs();
        assertEquals(1, logs.size());
        LogService.LogEntry logEntry = logs.getFirst();
        assertEquals("[INFO] Test message", logEntry.message());
        assertEquals("testMethod", logEntry.type());
        assertEquals(123L, logEntry.timestamp());
    }

    @Test
    void testGetLogs() {
        LogService logHandler = new LogService();

        logHandler.publish(logRecord(Level.INFO, "Test message 1", "testMethod1", 1L));
        logHandler.publish(logRecord(Level.INFO, "Test message 2", "testMethod2", 2L));

        List<LogService.LogEntry> logs = logHandler.getLogs();
        assertEquals(2, logs.size());
    }

    @Test
    void testLogLimit() {
        LogService logHandler = new LogService();

        for (int i = 0; i < 1001; i++) {
            logHandler.publish(logRecord(Level.INFO, "Test message " + i, "testMethod", i));
        }

        List<LogService.LogEntry> logs = logHandler.getLogs();
        assertEquals(1000, logs.size());
        assertEquals("[INFO] Test message 1", logs.getFirst().message());
    }

    @Test
    void testLogOrder() {
        LogService logHandler = new LogService();

        logHandler.publish(logRecord(Level.INFO, "Test message 1", "testMethod1", 1L));
        logHandler.publish(logRecord(Level.INFO, "Test message 2", "testMethod2", 2L));

        List<LogService.LogEntry> logs = logHandler.getLogs();
        assertTrue(logs.get(0).timestamp() <= logs.get(1).timestamp());
    }

    @Test
    void testGetLogsReturnsCopy() {
        LogService logHandler = new LogService();

        logHandler.publish(logRecord(Level.INFO, "Test message", "testMethod", 1L));
        List<LogService.LogEntry> logs = logHandler.getLogs();
        logs.clear();

        assertEquals(1, logHandler.getLogs().size());
    }

    private static LogRecord logRecord(Level level, String message, String methodName, long timestamp) {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName(LogService.LOGGER_NAME);
        record.setSourceClassName(DemoDataService.class.getName());
        record.setSourceMethodName(methodName);
        record.setMillis(timestamp);
        return record;
    }
}
