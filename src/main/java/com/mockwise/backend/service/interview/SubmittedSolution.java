package com.mockwise.backend.service.interview;

import com.mockwise.backend.repository.question.ProgrammingLanguage;

import java.util.UUID;

/**
 * One solution handed to {@link InterviewService#endInterview}. The HTTP body stays in
 * {@code controller/interview/dto}.
 */
public record SubmittedSolution(
        UUID questionId,
        String code,
        ProgrammingLanguage language,
        String timeComplexity,
        String spaceComplexity
) {
}
