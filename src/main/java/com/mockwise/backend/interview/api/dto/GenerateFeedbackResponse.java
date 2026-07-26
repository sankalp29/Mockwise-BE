package com.mockwise.backend.interview.api.dto;

/**
 * Action-ack for POST /generate-feedback.
 * {@code status} retains legacy lowercase values; {@code statusCode} is stable for clients.
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

    public static GenerateFeedbackResponse alreadyReady(String interviewId) {
        return new GenerateFeedbackResponse(
                interviewId,
                "started",
                "ALREADY_READY",
                "Feedback is already available.");
    }
}
