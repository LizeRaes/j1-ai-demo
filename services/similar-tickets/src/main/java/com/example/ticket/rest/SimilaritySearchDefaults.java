package com.example.ticket.rest;

record SimilaritySearchDefaults(int maxResults, double minScore) {

    SimilaritySearchDefaults {
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be greater than zero");
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be between 0.0 and 1.0");
        }
    }
}
