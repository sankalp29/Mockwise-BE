package com.mockwise.backend.controller.interview.dto;

/**
 * Action-ack for POST /generate-feedback.
 * {@code statusCode} matches {@link com.mockwise.backend.service.interview.FeedbackRequestOutcome}.
 */
public record GenerateFeedbackResponse(
        String interviewId,
        String status,
        String statusCode,
        String message
) {
    public static GenerateFeedbackResponse started(String interviewId) {
        return new GenerateFeedbackResponse(
                interviewId,
                "started",
                "STARTED",
                "Feedback generation has been started.");
    }

    public static GenerateFeedbackResponse ready(String interviewId) {
        return new GenerateFeedbackResponse(
                interviewId,
                "ready",
                "READY",
                "Feedback is already available.");
    }
}
