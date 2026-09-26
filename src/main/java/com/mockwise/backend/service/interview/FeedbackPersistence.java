package com.mockwise.backend.service.interview;

import com.mockwise.backend.service.dashboard.DashboardService;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.submission.UserSubmission;
import com.mockwise.backend.repository.submission.UserSubmissionRepository;
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
            Interview interview = interviewRepository.findById(interviewId).orElse(null);
            if (interview == null) {
                log.warn("Interview not found for post-processing: {}", interviewId);
                return;
            }
            if (!Boolean.TRUE.equals(interview.getAggregated())) {
                log.info("Updating dashboard aggregate for interview: {}", interview.getId());
                dashboardService.updateDashboardAggregate(interview);
                interview.setAggregated(true);
                interviewRepository.save(interview);
            }
            log.info("Post-feedback processing completed for interview: {}", interview.getId());
        } catch (Exception e) {
            log.error("Post-feedback hooks failed: {}", e.getMessage(), e);
        }
    }

    private static String formatProblemStatement(String title, String description,
                                                 String example, String constraints) {
        StringBuilder statement = new StringBuilder();
        statement.append("Title: ").append(title).append("\n\n");
        statement.append("Description: ").append(description).append("\n\n");
        if (example != null) {
            statement.append("Example:\n").append(example).append("\n\n");
        }
        if (constraints != null) {
            statement.append("Constraints:\n").append(constraints);
        }
        return statement.toString();
    }
}
