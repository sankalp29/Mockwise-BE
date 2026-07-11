package com.mockwise.backend.dashboard.application;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure helper to parse overallRating from Claude feedback JSON.
 */
public final class RatingExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RatingExtractor() {}

    public static Double extractOverallRating(String feedbackJson) {
        if (feedbackJson == null || feedbackJson.isBlank()) {
            return null;
        }
        try {
            var node = MAPPER.readTree(feedbackJson);
            if (node.has("overallRating")) {
                return node.get("overallRating").asDouble();
            }
        } catch (Exception ignore) {
            // malformed feedback is treated as missing rating
        }
        return null;
    }
}
