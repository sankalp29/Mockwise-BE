package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.SupabaseAuthService;
import com.mockwise.mockwise_backend.judge0.Judge0Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final ClaudeService claudeService;
    private final DashboardAggregateRepository dashboardAggregateRepository;
    private final Judge0Service judge0Service;
    private final CodeGeneratorService codeGeneratorService;

    @Transactional
    public Interview startInterview(SupabaseAuthService.SupabaseUser user, Question.Difficulty difficulty, Integer numQuestions, Integer timeMinutes) {
        log.info("Starting interview for user: {} with difficulty: {}, questions: {}, time: {}", 
                 user.getEmail(), difficulty, numQuestions, timeMinutes);

        // Clean up any abandoned interviews for the user
        cleanUpAbandonedInterviews(user.getId());
        
        // Check if the user already has an ongoing interview AFTER cleanup
        List<Interview> ongoingInterviews = interviewRepository.findByUserIdAndStatus(user.getId(), Interview.Status.IN_PROGRESS);
        if (!ongoingInterviews.isEmpty()) {
            throw new IllegalStateException("User already has an interview in progress. Please complete or end the current interview before starting a new one.");
        }

        // Get random questions for this interview
        List<Question> assignedQuestions = getRandomQuestions(difficulty, numQuestions);
        log.info("Assigned {} questions to interview", assignedQuestions.size());

        if (assignedQuestions.isEmpty()) {
            throw new IllegalStateException("No questions found for the specified difficulty and count. Please try again with different parameters or ensure questions are available.");
        }
        
        Interview interview = new Interview();
        interview.setUserId(user.getId());
        interview.setUserEmail(user.getEmail());
        interview.setDifficulty(difficulty);
        interview.setNumQuestions(numQuestions);
        interview.setTimeMinutes(timeMinutes);
        interview.setStartedAt(Instant.now());
        interview.setStatus(Interview.Status.IN_PROGRESS);
        // Create InterviewQuestion entities and associate them with the interview
        List<InterviewQuestion> interviewQuestions = new ArrayList<>();
        for (int i = 0; i < assignedQuestions.size(); i++) {
            Question question = assignedQuestions.get(i);
            InterviewQuestion iq = new InterviewQuestion(interview, question, i);
            interviewQuestions.add(iq);
        }
        interview.setInterviewQuestions(interviewQuestions);
        interviewRepository.save(interview);
        
        // Force loading of the questions collection to prevent lazy loading issues
        interview.getInterviewQuestions().size(); // This triggers the lazy loading within the transaction
        
        return interview;
    }

    @Transactional
    public Mono<Interview> endInterview(UUID interviewId, List<SubmissionRequest> submissions) {
        log.info("Ending interview: {} with {} submissions", interviewId, submissions.size());

        return Mono.fromCallable(() -> interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found")))
                .flatMap(interview -> {
                    interview.setEndedAt(Instant.now());
                    interview.setStatus(Interview.Status.COMPLETED);

                    List<Mono<UserSubmission>> submissionMonos = submissions.stream()
                            .map(submissionReq -> questionRepository.findById(submissionReq.getQuestionId())
                                    .map(question -> {
                                        UserSubmission submission = new UserSubmission();
                                        submission.setInterview(interview);
                                        submission.setQuestion(question);
                                        submission.setCode(submissionReq.getCode());
                                        submission.setLanguage(submissionReq.getLanguage());
                                        submission.setSubmittedAt(Instant.now());
                                        return submission;
                                    })
                                    .orElseThrow(() -> new IllegalArgumentException("Question not found")))
                            .map(userSubmissionRepository::save)
                            .map(this::runTestCasesForSubmission)
                            .collect(Collectors.toList());

                    return Flux.merge(submissionMonos)
                            .then(Mono.fromCallable(() -> interviewRepository.save(interview)));
                });
    }

    private Mono<UserSubmission> runTestCasesForSubmission(UserSubmission submission) {
        log.info("Running test cases for submission: {}", submission.getId());
        Question question = submission.getQuestion();
        String language = submission.getLanguage();
        String userCode = submission.getCode();
    
        if (question.getTestCases() == null || question.getTestCases().isEmpty()) {
            log.warn("No test cases found for question: {}", question.getId());
            return Mono.just(submission);
        }
    
        String driverCode;
        String judge0LanguageId;
        try {
            judge0LanguageId = codeGeneratorService.getJudge0LanguageId(language);
            switch (language.toLowerCase()) {
                case "java":
                    driverCode = codeGeneratorService.generateJavaDriverCode(userCode, question, question.getTestCases());
                    break;
                case "python":
                    driverCode = codeGeneratorService.generatePythonDriverCode(userCode, question, question.getTestCases());
                    break;
                case "cpp":
                    driverCode = codeGeneratorService.generateCppDriverCode(userCode, question, question.getTestCases());
                    break;
                default:
                    log.error("Unsupported language for Judge0: {}", language);
                    submission.setJudge0Result("Unsupported language for Judge0");
                    return Mono.just(submission);
            }
        } catch (IllegalArgumentException e) {
            log.error("Error getting Judge0 language ID for {}: {}", language, e.getMessage());
            submission.setJudge0Result("Error: " + e.getMessage());
            return Mono.just(submission);
        }
    
        String finalDriverCode = driverCode;
    
        // Retry predicate: retry on IO/timeout, but not on IllegalArgumentException
        java.util.function.Predicate<Throwable> retryable = t ->
            (t instanceof IOException) || (t instanceof TimeoutException) || !(t instanceof IllegalArgumentException);
    
            return Mono.fromCallable(() ->
                        (Judge0Service.Judge0SubmissionResponse) judge0Service.submitCode(finalDriverCode, judge0LanguageId, "", "")
                )
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(
                    Retry.fixedDelay(3, Duration.ofSeconds(2))
                        .filter(retryable::test)
                        .doBeforeRetry(retrySignal ->
                            log.warn("Retrying Judge0 submission, attempt {} due to: {}",
                                    retrySignal.totalRetriesInARow() + 1,
                                    retrySignal.failure() == null ? "unknown" : retrySignal.failure().toString())
                        )
                        .onRetryExhaustedThrow((spec, signal) ->
                            new RuntimeException("Judge0 submission failed after multiple retries", signal.failure())
                        )
                )
                .flatMap((Judge0Service.Judge0SubmissionResponse judge0Response) -> {
                    log.info("Judge0 response for submission {}: {}", submission.getId(), judge0Response.getStatus());
                    submission.setJudge0Result(judge0Response.getStatus());
                    submission.setTestCaseResultsJson(judge0Response.getStdout());
                    submission.setStdout(judge0Response.getStdout());
                    submission.setStderr(judge0Response.getStderr());
                    submission.setCompileOutput(judge0Response.getCompile_output());
                    submission.setTime(judge0Response.getTime());
                    submission.setMemory(judge0Response.getMemory());
                
                    return Mono.fromCallable(() -> userSubmissionRepository.save(submission))
                               .subscribeOn(Schedulers.boundedElastic()); // wrap blocking call
                })
                .onErrorResume(e -> {
                    log.error("Error submitting code to Judge0 for submission {}: {}", submission.getId(), e.getMessage(), e);
                    submission.setJudge0Result("Error communicating with Judge0: " + e.getMessage());
                
                    return Mono.fromCallable(() -> userSubmissionRepository.save(submission)) // wrap blocking call
                               .subscribeOn(Schedulers.boundedElastic())
                               .onErrorResume(saveErr -> {
                                   log.error("Failed to save submission after Judge0 error: {}", saveErr.getMessage(), saveErr);
                                   return Mono.just(submission); // fallback
                               });
                });
                
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
        log.info("Generating Feedback for Submission: {}", submission.getId());
        Question question = submission.getQuestion();
        String problemStatement = formatProblemStatement(question);

        StringBuilder executionResultsSummary = new StringBuilder();
        executionResultsSummary.append("\n--- Code Execution Results ---\n");

        // Handle compilation errors
        if (submission.getCompileOutput() != null && !submission.getCompileOutput().isBlank()) {
            executionResultsSummary.append("Compilation Error:\n").append(submission.getCompileOutput()).append("\n");
        } else if (submission.getStderr() != null && !submission.getStderr().isBlank()) {
            // Handle runtime errors (if not a compilation error)
            executionResultsSummary.append("Runtime Error:\n").append(submission.getStderr()).append("\n");
            executionResultsSummary.append("Overall Judge0 Status: ").append(submission.getJudge0Result()).append("\n");
        } else if (submission.getTestCaseResultsJson() != null && !submission.getTestCaseResultsJson().isBlank()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> testCaseResults = objectMapper.readValue(
                    submission.getTestCaseResultsJson(), new TypeReference<List<Map<String, Object>>>() {}
                );

                int totalTests = testCaseResults.size();
                long passedTests = testCaseResults.stream().filter(r -> (Boolean)r.get("passed")).count();
                long failedTests = totalTests - passedTests;

                executionResultsSummary.append("Total Test Cases: ").append(totalTests).append("\n");
                executionResultsSummary.append("Passed: ").append(passedTests).append("\n");
                executionResultsSummary.append("Failed: ").append(failedTests).append("\n");

                if (failedTests > 0) {
                    executionResultsSummary.append("\nFailed Test Case Details:\n");
                    testCaseResults.stream()
                        .filter(r -> !(Boolean)r.get("passed"))
                        .forEach(r -> {
                            executionResultsSummary.append("  Test Case ID: ").append(r.get("testCaseId")).append("\n");
                            executionResultsSummary.append("  Input: ").append(r.get("input")).append("\n");
                            executionResultsSummary.append("  Expected Output: ").append(r.get("expectedOutput")).append("\n");
                            executionResultsSummary.append("  Actual Output: ").append(r.get("actualOutput")).append("\n");
                            executionResultsSummary.append("  Time (ms): ").append(r.get("time")).append("\n");
                            executionResultsSummary.append("  --------------------\n");
                        });
                }
                executionResultsSummary.append("Overall Judge0 Status: ").append(submission.getJudge0Result()).append("\n");
            } catch (Exception e) {
                log.error("Error parsing testCaseResultsJson for submission {}: {}", submission.getId(), e.getMessage());
                executionResultsSummary.append("Could not parse detailed test results: ").append(e.getMessage()).append("\n");
                executionResultsSummary.append("Raw Judge0 stdout:\n").append(submission.getStdout()).append("\n");
                executionResultsSummary.append("Overall Judge0 Status: ").append(submission.getJudge0Result()).append("\n");
            }
        } else {
            executionResultsSummary.append("No detailed test execution results available.\n");
            executionResultsSummary.append("Overall Judge0 Status: ").append(submission.getJudge0Result()).append("\n");
            // Include raw stdout/stderr if they contain relevant info not already covered
            if (submission.getStdout() != null && !submission.getStdout().isBlank()) {
                executionResultsSummary.append("Raw stdout: ").append(submission.getStdout()).append("\n");
            }
            if (submission.getStderr() != null && !submission.getStderr().isBlank()) {
                executionResultsSummary.append("Raw stderr: ").append(submission.getStderr()).append("\n");
            }
        }

        // Generate prompt for code feedback and call Claude
        String prompt = claudeService.buildCodeFeedbackPrompt(
            problemStatement,
            submission.getCode(),
            submission.getLanguage(),
            executionResultsSummary.toString() // Pass the execution results summary
        );
        
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
        log.info("Attempting to fetch {} random questions with difficulty: {}", count, difficulty);
        long startTime = System.nanoTime();

        List<UUID> questionIds = questionRepository.findIdsByDifficulty(difficulty);
        log.debug("Found {} question IDs for difficulty {}", questionIds.size(), difficulty);

        if (questionIds.size() < count) {
            log.warn("Not enough questions found for difficulty {}. Required: {}, Found: {}. Using all available.",
                     difficulty, count, questionIds.size());
            count = questionIds.size();
        }

        Collections.shuffle(questionIds);
        List<UUID> selectedIds = questionIds.stream().limit(count).toList();
        log.debug("Selected {} random question IDs after shuffling.", selectedIds.size());

        List<Question> questions = questionRepository.findAllById(selectedIds);
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1000000;
        log.info("Finished fetching {} random questions in {} ms.", questions.size(), durationMs);
        
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

        var recent = interviewRepository.findByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .filter(interview -> interview.getStatus() != Interview.Status.ABANDONED)
                .limit(5)
                .collect(Collectors.toList());

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

    // New method to fetch a language-specific code stub
    public String getCodeStub(UUID questionId, String language) {
        return questionRepository.findById(questionId)
                .map(question -> question.getCodeStubTemplates().stream()
                        .filter(template -> language.equalsIgnoreCase(template.getLanguage()))
                        .map(CodeStubTemplate::getStubContent)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Code stub template not found for language " + language + " for question " + questionId)))
                .orElseThrow(() -> new IllegalArgumentException("Question not found with ID: " + questionId));
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

    // Placeholder for syntax checking
    public List<String> checkSyntax(String code, String language) {
        log.info("Performing syntax check for language: {}", language);
        List<String> errors = new ArrayList<>();
        File tempDir = null;

        try {
            tempDir = Files.createTempDirectory("syntax_check_test").toFile();

            switch (language.toLowerCase()) {
                case "java":
                    return checkJavaSyntax(code, tempDir);
                case "python":
                    return checkPythonSyntax(code, tempDir);
                case "cpp":
                    return checkCppSyntax(code, tempDir);
                default:
                    errors.add("Unsupported language for syntax checking: " + language);
                    return errors;
            }
        } catch (Exception e) {
            log.error("Exception during syntax check for language {}", language, e);
            errors.add("Internal server error during syntax check: " + e.getMessage());
            return errors;
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir.toPath())
                         .sorted(java.util.Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                } catch (Exception e) {
                    log.warn("Failed to clean up temporary directory: {}", tempDir.getAbsolutePath(), e);
                }
            }
        }
    }

    private List<String> checkJavaSyntax(String code, File tempDir) throws Exception {
        List<String> errors = new ArrayList<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            errors.add("JDK not found. Please ensure a JDK is installed and JAVA_HOME is set correctly.");
            return errors;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        File sourceFile = new File(tempDir, "Solution.java");

        // Wrap the provided code in a class if it's not already
        String fullCode = code;
        if (!code.contains("class Solution")) {
            fullCode = "public class Solution {\n" + code + "\n}";
        }

        // Write the provided code to a temporary Java file
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(fullCode);
        }

        // Set up file manager and compilation task
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
        StringWriter output = new StringWriter();

        JavaCompiler.CompilationTask task = compiler.getTask(
            output, 
            fileManager, 
            diagnostics, 
            Arrays.asList("-d", tempDir.getAbsolutePath()), // Output class files to temp directory
            null, 
            compilationUnits);

        // Perform compilation
        boolean success = task.call();

        if (!success) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                String message = diagnostic.getMessage(Locale.getDefault());
                // Remove the temporary file path from the message
                message = message.replace(diagnostic.getSource().getName(), "Solution.java");
                errors.add(String.format("Error on line %d: %s",
                                        diagnostic.getLineNumber(),
                                        message));
            }
        } else {
            // Optionally, if compilation is successful but there are warnings, you can add them
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                    String message = diagnostic.getMessage(Locale.getDefault());
                    // Remove the temporary file path from the message
                    message = message.replace(diagnostic.getSource().getName(), "Solution.java");
                    errors.add(String.format("Warning on line %d: %s",
                                            diagnostic.getLineNumber(),
                                            message));
                }
            }
        }
        fileManager.close();
        return errors;
    }

    private List<String> executeCommand(List<String> command, File workingDir, String fileName) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true); // Redirect error stream to output stream

        Process process = processBuilder.start();
        // Read output from the process
        List<String> outputLines = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line.replace(workingDir.getAbsolutePath() + File.separator, "")); // Clean up path
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && outputLines.isEmpty()) {
            // If there's an error but no output, provide a generic message
            outputLines.add("Command execution failed with exit code " + exitCode + ". No specific error message provided by tool.");
        }
        return outputLines;
    }

    private List<String> checkPythonSyntax(String code, File tempDir) throws Exception {
        File sourceFile = new File(tempDir, "solution.py");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        }
        // Use python -m py_compile to check syntax
        List<String> command = Arrays.asList("python", "-m", "py_compile", sourceFile.getName());
        List<String> output = executeCommand(command, tempDir, sourceFile.getName());
        if (output.isEmpty()) {
            return List.of(); // No errors
        } else {
            // Filter out non-error output like "Syntax producing bytecode: ..."
            return output.stream()
                         .filter(line -> line.contains("Error") || line.contains("SyntaxError"))
                         .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll);
        }
    }

    private List<String> checkCppSyntax(String code, File tempDir) throws Exception {
        File sourceFile = new File(tempDir, "solution.cpp");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        }
        // Use g++ -fsyntax-only to check syntax
        List<String> command = Arrays.asList("g++", "-fsyntax-only", sourceFile.getName());
        List<String> output = executeCommand(command, tempDir, sourceFile.getName());
        return output; // g++ only outputs errors to stderr, which is redirected to output
    }

    private void cleanUpAbandonedInterviews(String userId) {
        log.info("Cleaning up abandoned interviews for user: {}", userId);
        List<Interview> inProgressInterviews = interviewRepository.findByUserIdAndStatus(
            userId, Interview.Status.IN_PROGRESS
        );

        if (inProgressInterviews.isEmpty()) {
            log.info("No in-progress interviews found for user: {}", userId);
            return;
        }

        List<Interview> abandonedInterviews = inProgressInterviews.stream()
            .filter(interview -> {
                Instant plannedEndTime = interview.getStartedAt().plus(interview.getTimeMinutes(), ChronoUnit.MINUTES);
                // An interview is considered abandoned if its planned end time plus a grace period (e.g., 1 minute)
                // is before the current instant.
                return plannedEndTime.plus(1, ChronoUnit.MINUTES).isBefore(Instant.now());
            })
            .toList();

        if (abandonedInterviews.isEmpty()) {
            log.info("No time-expired in-progress interviews found for user: {}", userId);
            return;
        }

        log.info("Found {} abandoned interviews for user: {}", abandonedInterviews.size(), userId);
        abandonedInterviews.forEach(interview -> {
            log.warn("Interview {} (started at {}) is abandoned. Marking it as ABANDONED.", 
                     interview.getId(), interview.getStartedAt());
            interview.setEndedAt(Instant.now());
            interview.setStatus(Interview.Status.ABANDONED);
            interviewRepository.save(interview);
        });
        log.info("Successfully cleaned up {} abandoned interviews for user: {}", abandonedInterviews.size(), userId);
    }
}
