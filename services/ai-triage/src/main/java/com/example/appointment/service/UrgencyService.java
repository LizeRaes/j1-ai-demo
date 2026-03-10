package com.example.appointment.service;

import com.example.appointment.dto.UrgencyScoreRequest;
import com.example.appointment.dto.UrgencyScoreResponse;
import com.example.appointment.external.UrgencyClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class UrgencyService {

    @Inject
    @RestClient
    UrgencyClient urgencyClient;

    public double score(String complaint) {
        UrgencyScoreResponse response = urgencyClient.score(new UrgencyScoreRequest(complaint));
        if (response == null) {
            throw new IllegalStateException("Urgency score no response");
        }

        double score = response.score();
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0 || score > 10.0) {
            throw new IllegalStateException("Urgency score: invalid score " + score);
        }
        return score;
    }

    public int scoreAsInt(String complaint) {
        return (int) Math.round(score(complaint));
    }
}
