package com.example.document.service;

import com.example.document.dto.LogsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class LogService {

    private final Queue<LogsResponse.LogInfo> logs = new ConcurrentLinkedQueue<>();
    private static final int MAX_LOGS = 1000;

    public void addLog(String message, String type) {
        logs.offer(new LogsResponse.LogInfo(message, type, System.currentTimeMillis()));
        while (logs.size() > MAX_LOGS) {
            logs.poll();
        }
    }

    public List<LogsResponse.LogInfo> getLogs() {
        return new ArrayList<>(logs);
    }

}
