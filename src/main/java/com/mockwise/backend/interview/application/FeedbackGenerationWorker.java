package com.mockwise.backend.interview.application;

import com.mockwise.backend.evaluation.ClaudeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Async Claude feedback worker. <strong>No ownership checks</strong> — callers must gate first
 * (or be a trusted path such as post-submit after {@code endInterview}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedbackGenerationWorker {

    private final FeedbackPersistence feedbackPersistence;
    private final ClaudeService claudeService;

    /**
     * Fire-and-forget Claude loop. Must not be {@code @Transactional}.
     * Internal errors are logged and swallowed so status stays PENDING / soft FAILED.
     */
    @Async("feedbackTaskExecutor")
    public void runFeedbackGeneration(UUID interviewId) {
        log.info("Generating Claude feedback for interview: {}", interviewId);
        try {
            List<FeedbackPersistence.SubmissionPromptData> prompts =
                    feedbackPersistence.loadPromptData(interviewId);
            log.info("Found {} submissions for interview: {}", prompts.size(), interviewId);

            for (FeedbackPersistence.SubmissionPromptData data : prompts) {
                generateFeedbackForSubmission(data);
            }

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

        String feedback = claudeService.callClaude(prompt);
        log.info("Received feedback for submission: {}", data.submissionId());

        feedbackPersistence.saveFeedback(data.submissionId(), feedback);
    }
}
