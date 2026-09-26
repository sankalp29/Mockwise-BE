package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.service.dashboard.RatingExtractor;
import com.mockwise.backend.service.evaluation.ClaudeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Asks Claude to review a design drawing. The rubric lives on {@link DesignEvaluation}.
 * Call this outside a database transaction.
 */
@Component
@RequiredArgsConstructor
public class DesignReviewer {

    private final ClaudeService claudeService;

    public Review review(DesignPrompt prompt, String scene, String caption) {
        String body = claudeService.complete(DesignEvaluation.of(prompt, scene, caption));
        Double rating = RatingExtractor.extractOverallRating(body);
        int score = rating == null ? 0 : (int) Math.round(rating);
        return new Review(body, score);
    }

    public record Review(String body, int rating) {
    }
}
