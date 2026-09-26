package com.mockwise.backend.controller.admin.dto;

import java.util.List;

import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.question.ProgrammingLanguage;

/**
 * One request creates the question only when every supported language has a stub and an optimal solution.
 * {@code description}, {@code example}, and {@code constraints} are Markdown.
 */
public record CreateQuestionRequest(
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
