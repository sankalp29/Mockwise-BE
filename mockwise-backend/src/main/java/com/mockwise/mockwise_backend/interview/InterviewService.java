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
    private final DashboardAggregateRepository dashboardAggregateRepository;

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

        List<UserSubmission> submissions = userSubmissionRepository.findByInterviewId(interviewId);
        log.info("Found {} submissions for interview: {}", submissions.size(), interviewId);
        return Flux.fromIterable(submissions)
                .flatMap(this::generateFeedbackForSubmission)
                .then(Mono.fromRunnable(() -> {
                    log.info("*****POST-FEEDBACK HOOKS STARTING for interview: {}******", interviewId);
                    try {
                        Interview iv = interviewRepository.findById(interviewId).orElse(null);
                        if (iv != null) {
                            log.info("Found interview for post-processing: {}, aggregated: {}", iv.getId(), iv.getAggregated());
                            if (Boolean.FALSE.equals(iv.getAggregated())) {
                                log.info("Updating dashboard aggregate for interview: {}", iv.getId());
                                updateDashboardAggregate(iv);
                                iv.setAggregated(true);
                                interviewRepository.save(iv);
                            }
                            log.info("Calling computeAIInsights for user: {}", iv.getUserId());
                            computeAIInsights(iv.getUserId());
                        } else {
                            log.warn("Interview not found for post-processing: {}", interviewId);
                        }
                    } catch (Exception e) {
                        log.error("Post-feedback hooks failed: {}", e.getMessage(), e);
                    }
                }))
                .doOnSuccess(v -> log.info("Successfully generated feedback for all submissions in interview: {}", interviewId))
                .doOnError(e -> log.error("Error generating feedback for interview: {}", interviewId, e))
                .then();
    }

    private void updateDashboardAggregate(Interview interview) {
        String userId = interview.getUserId();
        DashboardAggregate agg = dashboardAggregateRepository.findByUserId(userId).orElseGet(() -> {
            DashboardAggregate a = new DashboardAggregate();
            a.setUserId(userId);
            a.setLowestScore(10.0);
            return a;
        });

        long seconds = 0L;
        if (interview.getEndedAt() != null && interview.getStartedAt() != null) {
            seconds = Math.max(0, interview.getEndedAt().getEpochSecond() - interview.getStartedAt().getEpochSecond());
        }

        List<UserSubmission> subs = userSubmissionRepository.findByInterviewId(interview.getId());
        double[] ratings = subs.stream()
                .map(UserSubmission::getClaudeFeedback)
                .filter(f -> f != null && !f.isBlank())
                .map(this::extractOverallRating)
                .filter(r -> r != null && r >= 0)
                .mapToDouble(Double::doubleValue)
                .toArray();
        double overall = ratings.length == 0 ? 0.0 : java.util.Arrays.stream(ratings).average().orElse(0.0);

        agg.setTotalMocks(agg.getTotalMocks() + 1);
        agg.setTotalTimeSpentSeconds(agg.getTotalTimeSpentSeconds() + seconds);
        agg.setSumOverallRating(agg.getSumOverallRating() + overall);
        agg.setRatingCount(agg.getRatingCount() + 1);
        agg.setHighestScore(Math.max(agg.getHighestScore(), overall));
        agg.setLowestScore(Math.min(agg.getLowestScore(), overall));
        agg.setTotalQuestions(agg.getTotalQuestions() + interview.getNumQuestions());
        if (agg.getLastMockDate() == null || interview.getStartedAt().isAfter(agg.getLastMockDate())) {
            agg.setLastMockDate(interview.getStartedAt());
        }

        switch (interview.getDifficulty()) {
            case EASY -> { agg.setSumEasy(agg.getSumEasy() + overall); agg.setCntEasy(agg.getCntEasy() + 1); }
            case MEDIUM -> { agg.setSumMedium(agg.getSumMedium() + overall); agg.setCntMedium(agg.getCntMedium() + 1); }
            case HARD -> { agg.setSumHard(agg.getSumHard() + overall); agg.setCntHard(agg.getCntHard() + 1); }
        }

        agg.setUpdatedAt(Instant.now());
        dashboardAggregateRepository.save(agg);
    }

    private Mono<UserSubmission> generateFeedbackForSubmission(UserSubmission submission) {
        log.info("*****GENERATING FEEDBACK FOR SUBMISSION: {}******", submission.getId());
        Question question = submission.getQuestion();
        String problemStatement = formatProblemStatement(question);
        
        // Generate prompt for code feedback and call Claude
        String prompt = claudeService.buildCodeFeedbackPrompt(problemStatement, submission.getCode(), submission.getLanguage());
        log.info("Calling Claude API for submission: {}", submission.getId());
        return claudeService.callClaude(prompt)
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

    // Dashboard helpers
    public List<Interview> getInterviewsForUser(String userId) {
        return interviewRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, Interview.Status.COMPLETED);
    }

    public List<UserSubmission> getSubmissionsForUser(String userId) {
        // naive approach: fetch interviews then submissions per interview
        List<Interview> interviews = getInterviewsForUser(userId);
        java.util.List<UserSubmission> all = new java.util.ArrayList<>();
        for (Interview i : interviews) {
            all.addAll(userSubmissionRepository.findByInterviewId(i.getId()));
        }
        return all;
    }

    public Double extractOverallRating(String feedbackJson) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(feedbackJson);
            if (node.has("overallRating")) return node.get("overallRating").asDouble();
        } catch (Exception ignore) {}
        return null;
    }

    public String getInsightsJson(String userId) {
        return dashboardAggregateRepository.findByUserId(userId)
                .map(DashboardAggregate::getInsightsJson)
                .orElse(null);
    }

    // AI Insights: compute if a new interview exists since last cache
    public void computeAIInsights(String userId) {
        log.info("*****STARTING AI INSIGHTS GENERATION for user: {}******", userId);
        DashboardAggregate agg = dashboardAggregateRepository.findByUserId(userId).orElse(null);
        if (agg == null) {
            log.warn("No DashboardAggregate found for user: {}", userId);
            return;
        }
        int interviewCount = agg.getTotalMocks();

        var recent = interviewRepository.findByUserIdOrderByStartedAtDesc(userId);
        recent = recent.subList(0, Math.min(5, recent.size()));
        var subs = new java.util.ArrayList<UserSubmission>();
        for (Interview i : recent) {
            subs.addAll(userSubmissionRepository.findByInterviewId(i.getId()));
        }

        StringBuilder ctx = new StringBuilder();
        for (UserSubmission s : subs) {
            if (s.getClaudeFeedback() != null) {
                ctx.append("\nSubmission Feedback:\n").append(s.getClaudeFeedback()).append("\n");
            }
        }
        log.info("**** Previous Feedbacks Context:**** {}", ctx.toString());
        // Generate prompt for AI insights and call Claude
        String prompt = claudeService.buildAIInsightsPrompt(ctx.toString());
        String insightsJson = claudeService.callClaude(prompt).blockOptional().orElse("{}");
        log.info("AI Insights JSON: {}", insightsJson);
        agg.setInsightsJson(insightsJson);
        agg.setInsightsInterviewCount(interviewCount);
        agg.setUpdatedAt(Instant.now());
        dashboardAggregateRepository.save(agg);
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
