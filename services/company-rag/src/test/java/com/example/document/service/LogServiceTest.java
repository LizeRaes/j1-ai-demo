package com.example.document.service;

import com.example.document.dto.LogsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LogServiceTest {

    private LogService logService;

    @BeforeEach
    void setUp() {
        logService = new LogService();
    }

    @Test
    void addLogCreatesLogEntry() {
        String message = "Test message";
        String type    = "debug";

        logService.addLog(message, type);

        List<LogsResponse.LogInfo> logs = logService.getLogs();
        assertEquals(1, logs.size(), "Exactly one log entry should be stored");

        LogsResponse.LogInfo entry = logs.getFirst();
        assertEquals(message, entry.message(), "Message must be preserved");
        assertEquals(type,    entry.type(),    "Type must be preserved");

    }

    @Test
    void getLogs() {
        logService.addLog("first", "info");
        List<LogsResponse.LogInfo> firstRead = logService.getLogs();

        firstRead.clear();
        firstRead.add(new LogsResponse.LogInfo("tampered", "hack", System.currentTimeMillis()));

        List<LogsResponse.LogInfo> secondRead = logService.getLogs();
        assertEquals(1, secondRead.size(), "Internal log storage must not be altered by the caller");
        assertEquals("first", secondRead.getFirst().message(), "Original message must still be present");
    }

    @Test
    void addLogEnforcesMaximumSize() {
        final int max = 1000;
        final int extra = 10;
        final int total = max + extra;

        for (int i = 0; i < total; i++) {
            logService.addLog("msg-" + i, "type");
        }

        List<LogsResponse.LogInfo> logs = logService.getLogs();

        assertEquals(max, logs.size(),
                "LogService should keep at most " + max + " entries");

        assertFalse(logs.stream()
                        .anyMatch(e -> e.message().equals("msg-0")),
                "The very first log entry should have been removed");

        assertTrue(logs.stream()
                        .anyMatch(e -> e.message().equals("msg-" + (total - 1))),
                "The latest log entry must still be stored");
    }

    @AfterEach
    void tearDown() {
        logService = null;
    }
}
