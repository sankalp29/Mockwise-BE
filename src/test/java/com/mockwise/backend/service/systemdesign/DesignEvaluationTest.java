package com.mockwise.backend.service.systemdesign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignEvaluationTest {

    @Test
    void promptCarriesTheDesignPromptAndTheDrawing() {
        String prompt = DesignEvaluation.of(samplePrompt(), sceneWithShape(), "cache the reads").prompt();

        assertTrue(prompt.contains("URL Shortener"));
        assertTrue(prompt.contains("Shorten links"));
        assertTrue(prompt.contains("Redirect in one hop"));
        assertTrue(prompt.contains("No custom hash"));
        assertTrue(prompt.contains(sceneWithShape()));
        assertTrue(prompt.contains("cache the reads"));
        assertTrue(prompt.contains("\"problemFraming\""));
        assertTrue(prompt.contains("\"tradeoffs\""));
    }

    @Test
    void emptyBoardFallsBackToALowArchitectureScore() throws Exception {
        String body = DesignEvaluation.of(samplePrompt(), "[]", null).fallback("client missing");
        var json = new ObjectMapper().readTree(body);

        assertEquals(3, json.get("overallRating").asInt());
        assertEquals(3, json.get("architecture").get("score").asInt());
        assertTrue(json.get("overallFeedback").asText().contains("client missing"));
        assertTrue(json.has("problemFraming"));
    }

    @Test
    void drawnBoardFallsBackHigherThanAnEmptyBoard() throws Exception {
        String body = DesignEvaluation.of(samplePrompt(), sceneWithShape(), "notes").fallback("offline");
        var json = new ObjectMapper().readTree(body);

        assertTrue(json.get("overallRating").asInt() > 3);
        assertTrue(json.get("architecture").get("feedback").asText().contains("shapes"));
    }

    private static DesignPrompt samplePrompt() {
        DesignPrompt prompt = new DesignPrompt();
        prompt.setTitle("URL Shortener");
        prompt.setBrief("Shorten links");
        prompt.setRequirements("Redirect in one hop");
        prompt.setConstraints("No custom hash");
        return prompt;
    }

    private static String sceneWithShape() {
        return "{\"shapes\":[{\"type\":\"box\",\"label\":\"api\"},{\"type\":\"box\",\"label\":\"db\"}]}";
    }
}
