package com.mockwise.backend.interview.application;

import com.mockwise.backend.dashboard.application.DashboardService;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Short-lived JPA transactions for feedback. Never call Claude from here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackPersistence {

    private final UserSubmissionRepository userSubmissionRepository;
    private final InterviewRepository interviewRepository;
    private final DashboardService dashboardService;

    @Transactional(readOnly = true)
    public List<UserSubmission> loadSubmissionsWithQuestions(UUID interviewId) {
        return userSubmissionRepository.findByInterviewIdWithQuestion(interviewId);
    }

    /**
     * Snapshot of fields needed for Claude prompts so entities are not used
     * after the read transaction ends (no open session / connection).
     */
    public record SubmissionPromptData(
            UUID submissionId,
            String code,
            String language,
            String userTimeComplexity,
            String userSpaceComplexity,
            String problemStatement
    ) {}

    @Transactional(readOnly = true)
    public List<SubmissionPromptData> loadPromptData(UUID interviewId) {
        return userSubmissionRepository.findByInterviewIdWithQuestion(interviewId).stream()
                .map(s -> new SubmissionPromptData(
                        s.getId(),
                        s.getCode(),
                        s.getLanguage(),
                        s.getUserTimeComplexity(),
                        s.getUserSpaceComplexity(),
                        formatProblemStatement(
                                s.getQuestion().getTitle(),
                                s.getQuestion().getDescription(),
                                s.getQuestion().getExample(),
                                s.getQuestion().getConstraints())
                ))
                .toList();
    }

    @Transactional
    public void saveFeedback(UUID submissionId, String feedback) {
        UserSubmission submission = userSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        submission.setClaudeFeedback(feedback);
        submission.setFeedbackGeneratedAt(Instant.now());
        userSubmissionRepository.save(submission);
    }

    @Transactional
    public void performPostFeedbackProcessing(UUID interviewId) {
        log.info("Post-Feedback hooks starting for interview: {}", interviewId);
        try {
            Interview iv = interviewRepository.findById(interviewId).orElse(null);
            if (iv == null) {
                log.warn("Interview not found for post-processing: {}", interviewId);
                return;
            }
            if (!Boolean.TRUE.equals(iv.getAggregated())) {
                log.info("Updating dashboard aggregate for interview: {}", iv.getId());
                dashboardService.updateDashboardAggregate(iv);
                iv.setAggregated(true);
                interviewRepository.save(iv);
            }
            log.info("Post-feedback processing completed for interview: {}", iv.getId());
        } catch (Exception e) {
            log.error("Post-feedback hooks failed: {}", e.getMessage(), e);
        }
    }

    private static String formatProblemStatement(String title, String description,
                                                 String example, String constraints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(title).append("\n\n");
        sb.append("Description: ").append(description).append("\n\n");
        if (example != null) {
            sb.append("Example:\n").append(example).append("\n\n");
        }
        if (constraints != null) {
            sb.append("Constraints:\n").append(constraints);
        }
        return sb.toString();
    }
}
