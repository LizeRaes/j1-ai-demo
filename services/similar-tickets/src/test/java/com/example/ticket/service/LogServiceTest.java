package com.example.ticket.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LogServiceTest {

    @Test
    void testAddLog() {
        LogService logService = new LogService();
        logService.addLog("Test message", "test-type");
        List<LogService.LogEntry> logs = logService.getLogs();
        assertEquals(1, logs.size());
        LogService.LogEntry logEntry = logs.getFirst();
        assertEquals("Test message", logEntry.message());
        assertEquals("test-type", logEntry.type());
        assertTrue(logEntry.timestamp() > 0);
    }

    @Test
    void testGetLogs() {
        LogService logService = new LogService();
        logService.addLog("Test message 1", "test-type-1");
        logService.addLog("Test message 2", "test-type-2");
        List<LogService.LogEntry> logs = logService.getLogs();
        assertEquals(2, logs.size());
    }

    @Test
    void testLogLimit() {
        LogService logService = new LogService();
        for (int i = 0; i < 1001; i++) {
            logService.addLog("Test message " + i, "test-type");
        }
        List<LogService.LogEntry> logs = logService.getLogs();
        assertEquals(1000, logs.size());
    }

    @Test
    void testLogOrder() {
        LogService logService = new LogService();
        logService.addLog("Test message 1", "test-type-1");
        logService.addLog("Test message 2", "test-type-2");
        List<LogService.LogEntry> logs = logService.getLogs();
        assertTrue(logs.get(0).timestamp() <= logs.get(1).timestamp());
    }

    @Test
    void testGetLogsReturnsCopy() {
        LogService logService = new LogService();
        logService.addLog("Test message", "test-type");
        logService.addLog("Another message", "another-type");
        assertEquals(2, logService.getLogs().size());
    }
}
