package com.mockwise.backend.dashboard.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RatingExtractorTest {

    @Test
    void extractsOverallRatingFromValidJson() {
        String json = "{\"overallRating\": 8.5, \"overallFeedback\": \"good\"}";
        assertEquals(8.5, RatingExtractor.extractOverallRating(json));
    }

    @Test
    void returnsNullWhenRatingMissing() {
        String json = "{\"overallFeedback\": \"ok\"}";
        assertNull(RatingExtractor.extractOverallRating(json));
    }

    @Test
    void returnsNullForBlankOrMalformed() {
        assertNull(RatingExtractor.extractOverallRating(null));
        assertNull(RatingExtractor.extractOverallRating(""));
        assertNull(RatingExtractor.extractOverallRating("not-json"));
    }
}
