package com.example.urgency.embedding;

public record CachedEmbedding(float[] embedding, double urgency, String text) {
    public float urgency01() {
        return (float) (urgency / 10.0);
    }
}
