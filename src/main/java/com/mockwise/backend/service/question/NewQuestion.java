package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.question.ProgrammingLanguage;
import com.mockwise.backend.repository.question.Question;

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
        Difficulty difficulty,
        List<LanguageCode> stubs,
        List<LanguageCode> optimalSolutions
) {
    public record LanguageCode(ProgrammingLanguage language, String code) {
    }
}
