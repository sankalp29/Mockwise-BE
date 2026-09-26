package com.mockwise.backend.service.systemdesign;

import org.springframework.stereotype.Component;

/**
 * Builds a written review from the drawing the candidate submitted.
 * A later reviewer can call an external model; this one keeps submit working offline.
 */
@Component
public class DesignReviewer {

    public Review review(String scene) {
        int marks = scene == null ? 0 : count(scene, "\"type\"");
        boolean drawn = marks > 0 && scene.length() > 40;
        int score = drawn ? Math.min(9, 6 + Math.min(3, marks / 2)) : 3;
        String architecture = drawn
                ? "The board has " + marks + " shapes. Walk through one request from the client to storage."
                : "The board was empty, so the architecture could not be evaluated.";
        String body = """
                {"problemFraming":{"score":%d,"feedback":"State the user, the write path, and the read path before adding extra boxes."},"architecture":{"score":%d,"feedback":"%s"},"scalability":{"score":%d,"feedback":"Name the bottleneck and what you would replicate or cache."},"tradeoffs":{"score":%d,"feedback":"Pick one decision, such as consistency versus latency, and say what you gave up."},"communication":{"score":%d,"feedback":"Label every box with the role it plays."},"strengths":["%s"],"improvements":["%s"],"overallFeedback":"%s","overallRating":%d}
                """.formatted(
                score,
                score,
                architecture,
                Math.max(3, score - 1),
                Math.max(3, score - 1),
                drawn ? score : 4,
                drawn ? "A diagram was submitted" : "The session was submitted",
                drawn ? "Add failure handling and data estimates" : "Draw the core request path",
                drawn
                        ? "The drawing gives a starting architecture. Tighten the data flow and the scale numbers next."
                        : "Submit a diagram of the core path so the review can talk about concrete components.",
                score
        );
        return new Review(body, score);
    }

    private static int count(String scene, String token) {
        int found = 0;
        int from = 0;
        while (from >= 0) {
            from = scene.indexOf(token, from);
            if (from < 0) {
                break;
            }
            found++;
            from += token.length();
        }
        return found;
    }

    public record Review(String body, int rating) {
    }
}
