package com.mockwise.backend.service.question;

import java.util.List;

/**
 * Use-case input for creating a question. Markdown fields are stored as written.
 * Every supported language must have one stub and one optimal solution.
 */
public record NewQuestion(
        String title,
        String description,
        String example,
        String constraints,
        String difficulty,
        List<LanguageCode> stubs,
        List<LanguageCode> optimalSolutions
) {
    public record LanguageCode(String language, String code) {
    }
}
