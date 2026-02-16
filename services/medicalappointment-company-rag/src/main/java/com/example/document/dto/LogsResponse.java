package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LogsResponse {
    @JsonProperty("logs")
    private List<LogInfo> logs;

    public LogsResponse() {
    }

    public LogsResponse(List<LogInfo> logs) {
        this.logs = logs;
    }

    public List<LogInfo> getLogs() {
        return logs;
    }

    public void setLogs(List<LogInfo> logs) {
        this.logs = logs;
    }

    public static class LogInfo {
        @JsonProperty("message")
        private String message;

        @JsonProperty("type")
        private String type;

        @JsonProperty("timestamp")
        private long timestamp;

        public LogInfo() {
        }

        public LogInfo(String message, String type, long timestamp) {
            this.message = message;
            this.type = type;
            this.timestamp = timestamp;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}
