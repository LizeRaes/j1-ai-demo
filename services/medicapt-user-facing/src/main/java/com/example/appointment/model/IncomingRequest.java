package com.example.appointment.model;

public class IncomingRequest {
    private String userId;
    private String channel;
    private String rawText;

    public IncomingRequest() {
    }

    public IncomingRequest(String userId, String channel, String rawText) {
        this.userId = userId;
        this.channel = channel;
        this.rawText = rawText;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
