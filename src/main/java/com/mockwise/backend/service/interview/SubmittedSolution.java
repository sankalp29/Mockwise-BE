package com.mockwise.backend.service.interview;

import java.util.UUID;

/**
 * One solution handed to {@link InterviewService#endInterview}. The HTTP body stays in
 * {@code controller/interview/dto}.
 */
public record SubmittedSolution(
        UUID questionId,
        String code,
        String language,
        String timeComplexity,
        String spaceComplexity
) {
}
