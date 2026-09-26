package com.mockwise.backend.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClaudeServiceTest {

    @Test
    void missingClientReturnsTheEvaluationFallback() {
        ClaudeService claude = new ClaudeService(new ObjectMapper());
        EvaluationSpec evaluation = new FixedEvaluation("design prompt", "design-fallback");

        String body = claude.complete(evaluation);

        assertEquals("design-fallback", body);
    }

    @Test
    void codingAndDesignFallbacksStayIndependent() {
        ClaudeService claude = new ClaudeService(new ObjectMapper());

        String coding = claude.complete(new FixedEvaluation("code prompt", "{\"kind\":\"coding\"}"));
        String design = claude.complete(new FixedEvaluation("design prompt", "{\"kind\":\"design\"}"));

        assertNotEquals(coding, design);
        assertEquals("{\"kind\":\"coding\"}", coding);
        assertEquals("{\"kind\":\"design\"}", design);
    }

    private record FixedEvaluation(String prompt, String fallback) implements EvaluationSpec {
        @Override
        public String fallback(String reason) {
            return fallback;
        }
    }
}
