package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.SupabaseAuthService;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final ClaudeService claudeService;
    private final DashboardAggregateRepository dashboardAggregateRepository;
    private final OptimalSolutionRepository optimalSolutionRepository;
    private final UserQuestionSeenRepository userQuestionSeenRepository;

    @Transactional
    public Interview startInterview(SupabaseAuthService.SupabaseUser user, Question.Difficulty difficulty, Integer numQuestions, Integer timeMinutes) {
        log.info("Starting interview for user: {} with difficulty: {}, questions: {}, time: {}", 
                 user.getEmail(), difficulty, numQuestions, timeMinutes);

        // Get random questions for this interview (user-specific to avoid seen questions)
        List<Question> assignedQuestions = getRandomQuestionsForUser(user.getId(), difficulty, numQuestions);
        log.info("Assigned {} questions to interview", assignedQuestions.size());

        Interview interview = new Interview();
        interview.setUserId(user.getId());
        interview.setUserEmail(user.getEmail());
        interview.setDifficulty(difficulty);
        interview.setNumQuestions(numQuestions);
        interview.setTimeMinutes(timeMinutes);
        interview.setStartedAt(Instant.now());
        interview.setStatus(Interview.Status.IN_PROGRESS);

        // Build link entities preserving order
        List<InterviewQuestion> links = new ArrayList<>();
        int order = 1;
        for (Question q : assignedQuestions) {
            links.add(new InterviewQuestion(interview, q, order++));
        }
        interview.setAssignedQuestionLinks(links);

        Interview saved = interviewRepository.saveAndFlush(interview);
        // Force initialization of assigned questions for response
        saved.getAssignedQuestions().size();
        return saved;
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
            submission.setUserTimeComplexity(submissionReq.getTimeComplexity());
            submission.setUserSpaceComplexity(submissionReq.getSpaceComplexity());
            submission.setSubmittedAt(Instant.now());

            userSubmissionRepository.save(submission);
        }

        Interview savedInterview = interviewRepository.save(interview);
        
        // Calculate and store overall rating for performance optimization
        calculateAndStoreOverallRating(savedInterview);
        
        // Mark questions as seen by the user
        markQuestionsAsSeen(interview.getUserId(), savedInterview);
        
        return savedInterview;
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
                                
                                // Recalculate overall rating after feedback is generated
                                calculateAndStoreOverallRating(iv);
                                
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
        String prompt = claudeService.buildCodeFeedbackPrompt(
            problemStatement,
            submission.getCode(),
            submission.getLanguage(),
            submission.getUserTimeComplexity(),
            submission.getUserSpaceComplexity());
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

    public List<Question> getRandomQuestionsForUser(String userId, Question.Difficulty difficulty, int count) {
        log.info("Requesting {} random questions with difficulty: {} for user: {}", count, difficulty, userId);
        
        List<Question> questions;
        
        if (userId != null) {
            // User-specific logic: exclude seen questions
            questions = getRandomQuestionsExcludingSeen(userId, difficulty, count);
        } else {
            // Original logic for backward compatibility
            questions = getRandomQuestionsOriginal(difficulty, count);
        }
        
        return questions;
    }
    
    private List<Question> getRandomQuestionsExcludingSeen(String userId, Question.Difficulty difficulty, int count) {
        // Get seen question IDs for this user and difficulty
        List<UUID> seenQuestionIds = userQuestionSeenRepository.findSeenQuestionIdsByUserAndDifficulty(userId, difficulty);
        log.info("User {} has seen {} questions of difficulty {}", userId, seenQuestionIds.size(), difficulty);
        
        // Get total questions count for this difficulty
        long totalCount = userQuestionSeenRepository.countTotalQuestionsByDifficulty(difficulty);
        long unseenCount = totalCount - seenQuestionIds.size();
        
        // Smart reset logic: if not enough unseen questions available, reset all seen questions
        if (unseenCount < count) {
            log.info("User {} has only {} unseen questions but requested {}. Resetting all seen questions for difficulty {}.", 
                    userId, unseenCount, count, difficulty);
            // Reset seen questions for this difficulty
            userQuestionSeenRepository.deleteByUserIdAndDifficulty(userId, difficulty);
            seenQuestionIds = new ArrayList<>(); // Clear the list
        }
        
        List<Question> questions;
        try {
            if (seenQuestionIds.isEmpty()) {
                // No seen questions, use original random query
                questions = questionRepository.findRandomQuestionsByDifficulty(difficulty.name(), count);
            } else {
                // Exclude seen questions
                questions = questionRepository.findRandomQuestionsByDifficultyExcluding(difficulty.name(), seenQuestionIds, count);
            }
            log.info("Found {} questions using random query (excluding seen)", questions.size());
        } catch (Exception e) {
            log.warn("Random query failed, using fallback: ", e);
            if (seenQuestionIds.isEmpty()) {
                List<Question> allQuestions = questionRepository.findByDifficultyString(difficulty);
                questions = allQuestions.stream().limit(count).toList();
            } else {
                questions = questionRepository.findByDifficultyExcluding(difficulty, seenQuestionIds);
                questions = questions.stream().limit(count).toList();
            }
            log.info("Found {} questions using fallback query (excluding seen)", questions.size());
        }
        
        return questions;
    }
    
    private List<Question> getRandomQuestionsOriginal(Question.Difficulty difficulty, int count) {
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
    
    /**
     * Calculate and store overall rating in the interview for performance optimization
     * This denormalizes the rating calculation to avoid complex joins in dashboard queries
     */
    public void calculateAndStoreOverallRating(Interview interview) {
        log.info("Calculating overall rating for interview: {}", interview.getId());
        
        try {
            List<UserSubmission> submissions = userSubmissionRepository.findByInterviewId(interview.getId());
            
            if (submissions.isEmpty()) {
                log.info("No submissions found for interview: {}, setting rating to 0.0", interview.getId());
                interview.setOverallRating(0.0);
                interviewRepository.save(interview);
                return;
            }
            
            double[] ratings = submissions.stream()
                    .map(UserSubmission::getClaudeFeedback)
                    .filter(f -> f != null && !f.isBlank())
                    .map(this::extractOverallRating)
                    .filter(r -> r != null && r >= 0)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            
            double overallRating = ratings.length == 0 ? 0.0 : java.util.Arrays.stream(ratings).average().orElse(0.0);
            
            // Round to 1 decimal place
            overallRating = Math.round(overallRating * 10.0) / 10.0;
            
            interview.setOverallRating(overallRating);
            interviewRepository.save(interview);
            
            log.info("Stored overall rating {} for interview: {}", overallRating, interview.getId());
            
        } catch (Exception e) {
            log.error("Error calculating overall rating for interview: {}", interview.getId(), e);
            // Set default rating to avoid null values
            interview.setOverallRating(0.0);
            interviewRepository.save(interview);
        }
    }

    /**
     * Mark questions as seen by a user when they complete an interview
     * Simplified approach to avoid connection leaks
     */
    public void markQuestionsAsSeen(String userId, Interview interview) {
        log.info("Marking questions as seen for user: {} in interview: {}", userId, interview.getId());
        
        try {
            Question.Difficulty difficulty = interview.getDifficulty();
            
            // Simple approach: mark each question individually with error handling
            for (InterviewQuestion link : interview.getAssignedQuestionLinks()) {
                UUID questionId = link.getQuestion().getId();
                
                try {
                    // Check if already exists to avoid duplicates
                    if (!userQuestionSeenRepository.existsByUserIdAndQuestionId(userId, questionId)) {
                        UserQuestionSeen seen = new UserQuestionSeen(userId, questionId, difficulty);
                        userQuestionSeenRepository.save(seen);
                    }
                } catch (Exception e) {
                    log.warn("Failed to mark question {} as seen for user {}: {}", questionId, userId, e.getMessage());
                    // Continue with other questions
                }
            }
            
            log.info("Completed marking questions as seen for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Error marking questions as seen for user: {}", userId, e);
            // Don't throw exception to avoid breaking interview completion
        }
    }

    // Dashboard helpers
    public List<Interview> getInterviewsForUser(String userId) {
        return interviewRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, Interview.Status.COMPLETED);
    }

    public List<UserSubmission> getSubmissionsForUser(String userId) {
        long startTime = System.currentTimeMillis();
        log.info("🚀 getSubmissionsForUser started for user: {}", userId);
        
        // OPTIMIZATION: Two-query approach for better performance
        // Step 1: Get interview IDs for the user
        List<Interview> interviews = getInterviewsForUser(userId);
        List<UUID> interviewIds = interviews.stream()
                .map(Interview::getId)
                .toList();
        
        log.info("📊 Found {} interviews for user: {}", interviews.size(), userId);
        
        if (interviewIds.isEmpty()) {
            log.info("⏱️ getSubmissionsForUser completed in {}ms (no interviews)", System.currentTimeMillis() - startTime);
            return new ArrayList<>();
        }
        
        // Step 2: Get all submissions for those interview IDs in one query
        long queryStartTime = System.currentTimeMillis();
        List<UserSubmission> submissions = userSubmissionRepository.findByInterviewIdIn(interviewIds);
        long queryEndTime = System.currentTimeMillis();
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("📈 Fetched {} submissions in {}ms (query: {}ms, total: {}ms)", 
                submissions.size(), (queryEndTime - queryStartTime), (queryEndTime - queryStartTime), totalTime);
        
        return submissions;
    }

    public Double extractOverallRating(String feedbackJson) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(feedbackJson);
            if (node.has("overallRating")) return node.get("overallRating").asDouble();
        } catch (Exception ignore) {}
        return null;
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

    public String getOptimalCode(UUID questionId, String language) {
        return optimalSolutionRepository
                .findByQuestionIdAndLanguage(questionId, language)
                .map(OptimalSolution::getCode)
                .orElse(null);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SubmissionRequest {
        private UUID questionId;
        private String code;
        private String language;
        private String timeComplexity;
        private String spaceComplexity;

        public SubmissionRequest(UUID questionId, String code, String language) {
            this.questionId = questionId;
            this.code = code;
            this.language = language;
        }
    }

    // Placeholder for syntax checking
    public List<String> checkSyntax(String code, String language) {
        log.info("Performing syntax check for language: {}", language);
        List<String> errors = new ArrayList<>();
        File tempDir = null;

        try {
            tempDir = Files.createTempDirectory("syntax_check").toFile();

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

    public Interview findOngoingInterviewByUserId(String userId) {
        List<Interview> inProgressInterviews = interviewRepository.findByUserIdAndStatus(userId, Interview.Status.IN_PROGRESS);
        
        for (Interview interview : inProgressInterviews) {
            // Check if the interview is still ongoing based on time
            if (isInterviewStillOngoing(interview)) {
                return interview;
            } else {
                // If interview time has expired, mark it as abandoned
                interview.setStatus(Interview.Status.ABANDONED);
                interview.setEndedAt(Instant.now());
                interviewRepository.save(interview);
                log.info("Interview {} expired and marked as abandoned", interview.getId());
            }
        }
        
        return null;
    }
    
    private boolean isInterviewStillOngoing(Interview interview) {
        Instant now = Instant.now();
        Instant startTime = interview.getStartedAt();
        long durationMinutes = interview.getTimeMinutes();
        
        // Calculate if the interview is still within its time limit
        long elapsedMinutes = java.time.Duration.between(startTime, now).toMinutes();
        
        return elapsedMinutes < durationMinutes;
    }

}
