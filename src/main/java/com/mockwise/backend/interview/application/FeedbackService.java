package com.mockwise.backend.interview.application;

import com.mockwise.backend.dashboard.application.DashboardService;
import com.mockwise.backend.evaluation.ClaudeService;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {

    private final UserSubmissionRepository userSubmissionRepository;
    private final InterviewRepository interviewRepository;
    private final ClaudeService claudeService;
    private final DashboardService dashboardService;

    public Mono<Void> generateFeedbackForInterview(UUID interviewId) {
        log.info("Generating Claude feedback for interview: {}", interviewId);

        return Mono.fromCallable(() -> userSubmissionRepository.findByInterviewIdWithQuestion(interviewId))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flatMap(submissions -> {
                    log.info("Found {} submissions for interview: {}", submissions.size(), interviewId);
                    return Flux.fromIterable(submissions)
                            .flatMap(this::generateFeedbackForSubmission)
                            .then(Mono.fromRunnable(() -> performPostFeedbackProcessing(interviewId))
                                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()));
                })
                .doOnSuccess(v -> log.info("Successfully generated feedback for all submissions in interview: {}", interviewId))
                .doOnError(e -> log.error("Error generating feedback for interview: {}", interviewId, e))
                .then();
    }

    @Transactional
    protected void performPostFeedbackProcessing(UUID interviewId) {
        log.info("Post-Feedback hooks starting for interview: {}", interviewId);
        try {
            Interview iv = interviewRepository.findById(interviewId).orElse(null);
            if (iv != null) {
                log.info("Found interview for post-processing: {}, aggregated: {}", iv.getId(), iv.getAggregated());
                if (Boolean.FALSE.equals(iv.getAggregated())) {
                    log.info("Updating dashboard aggregate for interview: {}", iv.getId());
                    dashboardService.updateDashboardAggregate(iv);
                    iv.setAggregated(true);
                    interviewRepository.save(iv);
                }
                log.info("Post-feedback processing completed for interview: {}", iv.getId());
            } else {
                log.warn("Interview not found for post-processing: {}", interviewId);
            }
        } catch (Exception e) {
            log.error("Post-feedback hooks failed: {}", e.getMessage(), e);
        }
    }

    private Mono<UserSubmission> generateFeedbackForSubmission(UserSubmission submission) {
        log.info("Generating feedback for submission: {}", submission.getId());
        Question question = submission.getQuestion();
        String problemStatement = formatProblemStatement(question);

        String prompt = claudeService.buildCodeFeedbackPrompt(
                problemStatement,
                submission.getCode(),
                submission.getLanguage(),
                submission.getUserTimeComplexity(),
                submission.getUserSpaceComplexity());
        log.info("Calling Claude API for submission: {}", submission.getId());

        return claudeService.callClaude(prompt)
                .flatMap(feedback -> saveFeedback(submission, feedback))
                .doOnError(e -> log.error("Error generating feedback for submission: {}", submission.getId(), e));
    }

    private Mono<UserSubmission> saveFeedback(UserSubmission submission, String feedback) {
        submission.setClaudeFeedback(feedback);
        submission.setFeedbackGeneratedAt(Instant.now());
        return Mono.just(userSubmissionRepository.save(submission));
    }

    private String formatProblemStatement(Question question) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(question.getTitle()).append("\n\n");
        sb.append("Description: ").append(question.getDescription()).append("\n\n");
        if (question.getExample() != null) {
            sb.append("Example:\n").append(question.getExample()).append("\n\n");
        }
        if (question.getConstraints() != null) {
            sb.append("Constraints:\n").append(question.getConstraints());
        }
        return sb.toString();
    }
}
