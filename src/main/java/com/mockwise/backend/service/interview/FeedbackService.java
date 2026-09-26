package com.mockwise.backend.service.interview;

import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.submission.UserSubmission;
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
 * or READY. Never put those gates only inside {@code @Async} methods.
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
    public FeedbackRequestOutcome requestFeedbackGeneration(UUID interviewId, String userId) {
        Interview interview = interviewService.requireOwnedInterview(interviewId, userId);
        List<UserSubmission> submissions = feedbackPersistence.loadSubmissionsWithQuestions(interviewId);
        if (submissions.isEmpty()) {
            throw new BadRequestException("Nothing to evaluate: interview has no submissions.");
        }

        FeedbackStatus status = deriveFeedbackStatus(interview, submissions);
        if (status == FeedbackStatus.READY) {
            return FeedbackRequestOutcome.READY;
        }

        // PENDING or soft FAILED → re-queue via Spring-proxied worker bean (not self-invocation)
        feedbackGenerationWorker.runFeedbackGeneration(interviewId);
        return FeedbackRequestOutcome.STARTED;
    }

    /**
     * Trusted async entry after ownership already proven (e.g. post-submit).
     * Delegates to the worker bean so {@code @Async} is honored.
     */
    public void runFeedbackGeneration(UUID interviewId) {
        feedbackGenerationWorker.runFeedbackGeneration(interviewId);
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
