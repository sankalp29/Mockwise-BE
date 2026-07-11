package com.mockwise.backend.interview.application;

import com.mockwise.backend.evaluation.ClaudeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Async interview feedback orchestration.
 * <p>
 * DB work is confined to short transactions in {@link FeedbackPersistence}.
 * Claude is called only while no transaction/connection is held.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final FeedbackPersistence feedbackPersistence;
    private final ClaudeService claudeService;

    /**
     * Fire-and-forget entry point. Runs on {@code feedbackTaskExecutor}.
     * Must not be {@code @Transactional} — that would hold a connection
     * for the entire Claude duration.
     */
    @Async("feedbackTaskExecutor")
    public void generateFeedbackForInterview(UUID interviewId) {
        log.info("Generating Claude feedback for interview: {}", interviewId);
        try {
            // 1) Short read TX — load prompt data, then release connection
            List<FeedbackPersistence.SubmissionPromptData> prompts =
                    feedbackPersistence.loadPromptData(interviewId);
            log.info("Found {} submissions for interview: {}", prompts.size(), interviewId);

            // 2) External I/O + short write TX per submission (no connection during Claude)
            for (FeedbackPersistence.SubmissionPromptData data : prompts) {
                generateFeedbackForSubmission(data);
            }

            // 3) Short write TX for dashboard aggregation
            feedbackPersistence.performPostFeedbackProcessing(interviewId);
            log.info("Successfully generated feedback for all submissions in interview: {}", interviewId);
        } catch (Exception e) {
            log.error("Error generating feedback for interview: {}", interviewId, e);
        }
    }

    private void generateFeedbackForSubmission(FeedbackPersistence.SubmissionPromptData data) {
        log.info("Generating feedback for submission: {}", data.submissionId());
        String prompt = claudeService.buildCodeFeedbackPrompt(
                data.problemStatement(),
                data.code(),
                data.language(),
                data.userTimeComplexity(),
                data.userSpaceComplexity());
        log.info("Calling Claude API for submission: {}", data.submissionId());

        // No open DB transaction here
        String feedback = claudeService.callClaude(prompt);
        log.info("Received feedback for submission: {}", data.submissionId());

        // Short write TX
        feedbackPersistence.saveFeedback(data.submissionId(), feedback);
    }
}
