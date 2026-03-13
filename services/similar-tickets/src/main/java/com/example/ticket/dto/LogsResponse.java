package com.example.ticket.dto;

import java.util.List;

public record LogsResponse(List<LogInfo> logs) {

    public record LogInfo(String message, String type, long timestamp) {
    }
}
