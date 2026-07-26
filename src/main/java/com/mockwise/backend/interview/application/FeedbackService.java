package com.mockwise.backend.interview.application;

import com.mockwise.backend.common.exception.BadRequestException;
import com.mockwise.backend.interview.api.dto.GenerateFeedbackResponse;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.submission.domain.UserSubmission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sync feedback gate + delegate to {@link FeedbackGenerationWorker} for async Claude work.
 * <p>
 * Ownership / lifecycle decisions complete on the request thread so HTTP can return 403/400
 * or ALREADY_READY. Never put those gates only inside {@code @Async} methods.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    /** Soft-FAILED when incomplete feedback older than this after interview end (or start). */
    static final Duration SOFT_FAILED_AFTER = Duration.ofMinutes(10);

    private final InterviewService interviewService;
    private final FeedbackPersistence feedbackPersistence;
    private final FeedbackGenerationWorker feedbackGenerationWorker;

    /**
     * Synchronous gate. Throws Forbidden / BadRequest. Schedules worker when work is needed.
     * Never {@code @Async}.
     */
    public GenerateFeedbackResponse requestFeedbackGeneration(UUID interviewId, String userId) {
        Interview interview = interviewService.requireOwnedInterview(interviewId, userId);
        List<UserSubmission> submissions = feedbackPersistence.loadSubmissionsWithQuestions(interviewId);
        if (submissions.isEmpty()) {
            throw new BadRequestException("Nothing to evaluate: interview has no submissions.");
        }

        FeedbackStatus status = deriveFeedbackStatus(interview, submissions);
        if (status == FeedbackStatus.READY) {
            return GenerateFeedbackResponse.alreadyReady(interviewId.toString());
        }

        // PENDING or soft FAILED → re-queue via Spring-proxied worker bean (not self-invocation)
        feedbackGenerationWorker.runFeedbackGeneration(interviewId);
        return GenerateFeedbackResponse.started(interviewId.toString());
    }

    /**
     * Trusted async entry after ownership already proven (e.g. post-submit).
     * Delegates to the worker bean so {@code @Async} is honored.
     */
    public void runFeedbackGeneration(UUID interviewId) {
        feedbackGenerationWorker.runFeedbackGeneration(interviewId);
    }

    /**
     * @deprecated Prefer {@link #runFeedbackGeneration(UUID)} or
     * {@link #requestFeedbackGeneration(UUID, String)}. Kept for existing integration tests.
     */
    @Deprecated
    public void generateFeedbackForInterview(UUID interviewId) {
        runFeedbackGeneration(interviewId);
    }

    static FeedbackStatus deriveFeedbackStatus(Interview interview, List<UserSubmission> submissions) {
        boolean allReady = submissions.stream().allMatch(FeedbackService::hasFeedback);
        if (allReady) {
            return FeedbackStatus.READY;
        }
        Instant anchor = interview.getEndedAt() != null ? interview.getEndedAt() : interview.getStartedAt();
        if (anchor != null && Instant.now().isAfter(anchor.plus(SOFT_FAILED_AFTER))) {
            return FeedbackStatus.FAILED;
        }
        return FeedbackStatus.PENDING;
    }

    private static boolean hasFeedback(UserSubmission s) {
        return s.getClaudeFeedback() != null && !s.getClaudeFeedback().isBlank();
    }
}
