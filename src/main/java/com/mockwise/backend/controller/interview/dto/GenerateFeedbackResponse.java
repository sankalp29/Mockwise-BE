package com.mockwise.backend.controller.interview.dto;

import com.mockwise.backend.service.interview.FeedbackRequestOutcome;

/**
 * Action-ack for POST /generate-feedback.
 * {@code statusCode} is {@link FeedbackRequestOutcome}: generation was started, or feedback is already ready.
 */
public record GenerateFeedbackResponse(
        String interviewId,
        String status,
        FeedbackRequestOutcome statusCode,
        String message
) {
    public static GenerateFeedbackResponse started(String interviewId) {
        return new GenerateFeedbackResponse(
                interviewId,
                "started",
                FeedbackRequestOutcome.STARTED,
                "Feedback generation has been started.");
    }

    public static GenerateFeedbackResponse ready(String interviewId) {
        return new GenerateFeedbackResponse(
                interviewId,
                "ready",
                FeedbackRequestOutcome.READY,
                "Feedback is already available.");
    }
}
