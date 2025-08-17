package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.SupabaseAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final ClaudeService claudeService;

    @Transactional
    public Interview startInterview(SupabaseAuthService.SupabaseUser user, Question.Difficulty difficulty, Integer numQuestions, Integer timeMinutes) {
        log.info("Starting interview for user: {} with difficulty: {}, questions: {}, time: {}", 
                 user.getEmail(), difficulty, numQuestions, timeMinutes);

        // Get random questions for this interview
        List<Question> assignedQuestions = getRandomQuestions(difficulty, numQuestions);
        log.info("Assigned {} questions to interview", assignedQuestions.size());

        Interview interview = new Interview();
        interview.setUserId(user.getId());
        interview.setUserEmail(user.getEmail());
        interview.setDifficulty(difficulty);
        interview.setNumQuestions(numQuestions);
        interview.setTimeMinutes(timeMinutes);
        interview.setStartedAt(Instant.now());
        interview.setStatus(Interview.Status.IN_PROGRESS);
        interview.setAssignedQuestions(assignedQuestions);

        return interviewRepository.save(interview);
    }

    @Transactional
    public Interview endInterview(UUID interviewId, List<SubmissionRequest> submissions) {
        log.info("Ending interview: {} with {} submissions", interviewId, submissions.size());

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found"));

        interview.setEndedAt(Instant.now());
        interview.setStatus(Interview.Status.COMPLETED);

        // Save all submissions
        for (SubmissionRequest submissionReq : submissions) {
            Question question = questionRepository.findById(submissionReq.getQuestionId())
                    .orElseThrow(() -> new IllegalArgumentException("Question not found"));

            UserSubmission submission = new UserSubmission();
            submission.setInterview(interview);
            submission.setQuestion(question);
            submission.setCode(submissionReq.getCode());
            submission.setLanguage(submissionReq.getLanguage());
            submission.setSubmittedAt(Instant.now());

            userSubmissionRepository.save(submission);
        }

        return interviewRepository.save(interview);
    }

    public Mono<Void> generateFeedbackForInterview(UUID interviewId) {
        log.info("Generating Claude feedback for interview: {}", interviewId);

        List<UserSubmission> submissions = userSubmissionRepository.findByInterview_Id(interviewId);
        log.info("Found {} submissions for interview: {}", submissions.size(), interviewId);
        return Flux.fromIterable(submissions)
                .flatMap(this::generateFeedbackForSubmission)
                .then()
                .doOnSuccess(v -> log.info("Successfully generated feedback for all submissions in interview: {}", interviewId))
                .doOnError(e -> log.error("Error generating feedback for interview: {}", interviewId, e));
    }

    private Mono<UserSubmission> generateFeedbackForSubmission(UserSubmission submission) {
        Question question = submission.getQuestion();
        String problemStatement = formatProblemStatement(question);

        return claudeService.evaluateCode(problemStatement, submission.getCode(), submission.getLanguage())
                .map(feedback -> {
                    log.info("Feedback for submission: {}", feedback);
                    submission.setClaudeFeedback(feedback);
                    submission.setFeedbackGeneratedAt(Instant.now());
                    return userSubmissionRepository.save(submission);
                })
                .doOnError(e -> log.error("Error generating feedback for submission: {}", submission.getId(), e));
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

    public List<Question> getRandomQuestions(Question.Difficulty difficulty, int count) {
        log.info("Requesting {} random questions with difficulty: {}", count, difficulty);
        
        // First check total questions available
        long totalQuestions = questionRepository.count();
        log.info("Total questions in database: {}", totalQuestions);
        
        List<Question> questions;
        try {
            questions = questionRepository.findRandomQuestionsByDifficulty(difficulty.name(), count);
            log.info("Found {} questions using random query", questions.size());
        } catch (Exception e) {
            log.warn("Random query failed, using fallback: ", e);
            List<Question> allQuestions = questionRepository.findByDifficultyString(difficulty);
            questions = allQuestions.stream().limit(count).toList();
            log.info("Found {} questions using fallback query", questions.size());
        }
        
        return questions;
    }

    public Interview getInterviewWithFeedback(UUID interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found"));
    }

    public List<UserSubmission> getSubmissionsWithFeedback(UUID interviewId) {
        return userSubmissionRepository.findByInterview_IdOrderBySubmittedAt(interviewId);
    }

    public Interview validateInterviewAccess(UUID interviewId, String userId) {
        log.info("Validating interview access: interviewId={}, userId={}", interviewId, userId);
        
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found"));
        
        if (!interview.getUserId().equals(userId)) {
            throw new SecurityException("User does not have access to this interview");
        }
        
        log.info("Interview access validated successfully for user: {}", userId);
        return interview;
    }

    public static class SubmissionRequest {
        private UUID questionId;
        private String code;
        private String language;

        // Constructors
        public SubmissionRequest() {}

        public SubmissionRequest(UUID questionId, String code, String language) {
            this.questionId = questionId;
            this.code = code;
            this.language = language;
        }

        // Getters and setters
        public UUID getQuestionId() { return questionId; }
        public void setQuestionId(UUID questionId) { this.questionId = questionId; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
}
