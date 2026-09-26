package com.mockwise.backend.controller.question.dto;

import java.util.UUID;

import com.mockwise.backend.repository.question.Question;

public record QuestionResponse(
        UUID id,
        String title,
        String description,
        String example,
        String constraints,
        Question.Difficulty difficulty
) {
    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getTitle(),
                question.getDescription(),
                question.getExample(),
                question.getConstraints(),
                question.getDifficulty());
    }
}
