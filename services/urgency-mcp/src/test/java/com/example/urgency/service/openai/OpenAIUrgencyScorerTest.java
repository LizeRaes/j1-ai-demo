package com.example.urgency.service.openai;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIUrgencyScorerTest {

    @Test
    void returnsNumericScoreFromModelResponse() {
        StubChatModel model = new StubChatModel("7.5");
        OpenAIUrgencyScorer scorer = new OpenAIUrgencyScorer(model, "Return a numeric urgency score.");

        double score = scorer.score("Patient cannot access urgent lab result.");

        assertEquals(7.5, score);
        assertTrue(model.prompt.contains("Return a numeric urgency score."));
        assertTrue(model.prompt.contains("Patient cannot access urgent lab result."));
    }

    @Test
    void clampsModelResponseToUrgencyRange() {
        assertEquals(10.0, new OpenAIUrgencyScorer(new StubChatModel("12.5"), "Score it").score("Emergency issue"));
        assertEquals(0.0, new OpenAIUrgencyScorer(new StubChatModel("-2"), "Score it").score("No issue"));
    }

    @Test
    void rejectsMissingNumericScore() {
        OpenAIUrgencyScorer scorer = new OpenAIUrgencyScorer(new StubChatModel("high urgency"), "Score it");

        assertThrows(IllegalStateException.class, () -> scorer.score("Please help"));
    }

    @Test
    void requiresComplaint() {
        OpenAIUrgencyScorer scorer = new OpenAIUrgencyScorer(new StubChatModel("5"), "Score it");

        assertThrows(IllegalArgumentException.class, () -> scorer.score(null));
        assertThrows(IllegalArgumentException.class, () -> scorer.score(" "));
    }


    @Test
    void requiresSettings() {
        assertThrows(IllegalArgumentException.class, () -> new OpenAIUrgencyScorer.Settings(" ", "Score it", 0.0, 20));
        assertThrows(IllegalArgumentException.class, () -> new OpenAIUrgencyScorer.Settings("gpt-4.1-mini", " ", 0.0, 20));
        assertThrows(IllegalArgumentException.class, () -> new OpenAIUrgencyScorer.Settings("gpt-4.1-mini", "Score it", 0.0, 0));
    }

    private static final class StubChatModel implements ChatModel {
        private final String response;
        private String prompt;

        private StubChatModel(String response) {
            this.response = response;
        }

        @Override
        public String chat(String prompt) {
            this.prompt = prompt;
            return response;
        }
    }
}
