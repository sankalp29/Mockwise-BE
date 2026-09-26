package com.mockwise.backend.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.backend.repository.question.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodingEvaluationTest {

    @Test
    void promptCarriesTheSubmissionAndTheCodingRubric() {
        CodingEvaluation evaluation = CodingEvaluation.from(sample("O(n)", "O(1)"));

        String prompt = evaluation.prompt();

        assertTrue(prompt.contains("Two Sum"));
        assertTrue(prompt.contains("return 1;"));
        assertTrue(prompt.contains("java"));
        assertTrue(prompt.contains("Time: O(n)"));
        assertTrue(prompt.contains("Space: O(1)"));
        assertTrue(prompt.contains("\"optimality\""));
        assertTrue(prompt.contains("overallRating"));
    }

    @Test
    void promptMarksMissingComplexityAsNotProvided() {
        String prompt = CodingEvaluation.from(sample(" ", null)).prompt();

        assertTrue(prompt.contains("Time: Not provided"));
        assertTrue(prompt.contains("Space: Not provided"));
    }

    @Test
    void fallbackIsCodingJson() throws Exception {
        String body = CodingEvaluation.from(sample("O(n)", "O(1)")).fallback("model down");
        var json = new ObjectMapper().readTree(body);

        assertEquals(7, json.get("overallRating").asInt());
        assertTrue(json.get("overallFeedback").asText().contains("model down"));
        assertTrue(json.has("timeComplexity"));
        assertTrue(json.get("timeComplexity").has("bigO"));
    }

    private static FeedbackPersistence.SubmissionPromptData sample(String time, String space) {
        return new FeedbackPersistence.SubmissionPromptData(
                UUID.randomUUID(),
                "return 1;",
                ProgrammingLanguage.JAVA,
                time,
                space,
                "Title: Two Sum");
    }
}
