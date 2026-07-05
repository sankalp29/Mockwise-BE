package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.AuthSupport;
import com.mockwise.mockwise_backend.auth.SupabaseAuthService;
import com.mockwise.mockwise_backend.common.exception.BadRequestException;
import com.mockwise.mockwise_backend.common.exception.ResourceGoneException;
import com.mockwise.mockwise_backend.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Slf4j
@Validated
public class InterviewController {

    private final InterviewService interviewService;
    private final QuestionCodeStubRepository questionCodeStubRepository;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startInterview(
            @Valid @RequestBody StartInterviewRequest request,
            Authentication authentication) {

        log.info("Starting interview: difficulty={}, numQuestions={}, timeMinutes={}",
                request.getDifficulty(), request.getNumQuestions(), request.getTimeMinutes());

        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);

        Interview interview = interviewService.startInterview(
                user,
                request.getDifficulty(),
                request.getNumQuestions(),
                request.getTimeMinutes()
        );

        List<Question> questions = interview.getAssignedQuestions();
        log.info("Interview {} created with {} questions for difficulty {}",
                interview.getId(), questions.size(), request.getDifficulty());

        return ResponseEntity.ok(Map.of(
                "interview", interview,
                "questions", questions
        ));
    }

    @PostMapping("/{interviewId}/submit")
    public ResponseEntity<Map<String, Object>> submitInterview(
            @PathVariable UUID interviewId,
            @Valid @RequestBody SubmitInterviewRequest request,
            Authentication authentication) {

        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);
        log.info("Submitting interview {} with {} submissions for user {}",
                interviewId, request.getSubmissions().size(), user.getId());

        Interview interview = interviewService.endInterview(interviewId, user.getId(), request.getSubmissions());
        interviewService.markQuestionsAsSeen(interview.getUserId(), interview);
        // Async — returns immediately
        interviewService.generateFeedbackForInterview(interviewId);

        return ResponseEntity.ok(Map.of(
                "message", "Interview submitted successfully",
                "interviewId", interview.getId().toString()
        ));
    }

    @GetMapping("/{interviewId}/feedback")
    public ResponseEntity<Map<String, Object>> getInterviewFeedback(
            @PathVariable UUID interviewId,
            Authentication authentication) {

        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);
        log.info("Getting feedback for interview {} for user {}", interviewId, user.getId());

        Interview interview = interviewService.getInterviewForUser(interviewId, user.getId());
        List<UserSubmission> submissions = interviewService.getSubmissionsWithFeedback(interviewId);

        return ResponseEntity.ok(Map.of(
                "interview", interview,
                "submissions", submissions
        ));
    }

    @GetMapping("/questions/{questionId}/stub")
    public ResponseEntity<String> getQuestionCodeStub(
            @PathVariable UUID questionId,
            @RequestParam String language,
            Authentication authentication) {

        AuthSupport.requireUser(authentication);

        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language parameter is required");
        }

        return questionCodeStubRepository
                .findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, language)
                .map(stub -> ResponseEntity.ok(stub.getStub()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No code stub available for question " + questionId + " and language " + language));
    }

    @PostMapping("/{interviewId}/generate-feedback")
    public ResponseEntity<Map<String, String>> generateFeedback(
            @PathVariable UUID interviewId,
            Authentication authentication) {

        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);
        // Ensures interview exists and caller owns it before kicking off async work
        interviewService.getInterviewForUser(interviewId, user.getId());
        interviewService.generateFeedbackForInterview(interviewId);
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(
            @RequestParam Question.Difficulty difficulty,
            @RequestParam(defaultValue = "3") @Min(1) int count,
            Authentication authentication) {

        SupabaseAuthService.SupabaseUser user = AuthSupport.optionalUser(authentication);
        String userId = user != null ? user.getId() : null;
        List<Question> questions = interviewService.getRandomQuestionsForUser(userId, difficulty, count);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{interviewId}/validate")
    public ResponseEntity<Map<String, Object>> validateInterviewSession(
            @PathVariable UUID interviewId,
            Authentication authentication) {

        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);
        log.info("Validating interview session {} for user {}", interviewId, user.getId());

        Interview interview = interviewService.validateInterviewAccess(interviewId, user.getId());

        long startTime = interview.getStartedAt().toEpochMilli();
        long totalTimeMs = interview.getTimeMinutes() * 60 * 1000L;
        long remainingMs = Math.max(0, totalTimeMs - (System.currentTimeMillis() - startTime));

        if (remainingMs <= 0) {
            throw new ResourceGoneException(
                    "Interview has ended",
                    Map.of("expired", true, "valid", false));
        }

        return ResponseEntity.ok(Map.of(
                "interview", interview,
                "questions", interview.getAssignedQuestions(),
                "remainingTimeMs", remainingMs,
                "valid", true
        ));
    }

    @GetMapping("/optimal-code")
    public ResponseEntity<Map<String, String>> getOptimalCode(
            @RequestParam UUID questionId,
            @RequestParam String language,
            Authentication authentication) {

        AuthSupport.requireUser(authentication);
        log.info("Fetching optimal code for questionId={}, language={}", questionId, language);

        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language parameter is required");
        }

        String code = interviewService.getOptimalCode(questionId, language);
        if (code == null || code.isBlank()) {
            throw new ResourceNotFoundException("Optimal code not found for question " + questionId
                    + " and language " + language);
        }
        return ResponseEntity.ok(Map.of("code", code));
    }

    @GetMapping("/test-auth")
    public ResponseEntity<Map<String, String>> testAuth(Authentication authentication) {
        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(Map.of("user", user.getEmail(), "status", "authenticated"));
    }

    @PostMapping("/check-syntax")
    public ResponseEntity<Map<String, Object>> checkSyntax(
            @Valid @RequestBody CheckSyntaxRequest request,
            Authentication authentication) {

        AuthSupport.requireUser(authentication);
        log.info("Checking syntax for language: {}", request.getLanguage());
        List<String> errors = interviewService.checkSyntax(request.getCode(), request.getLanguage());
        return ResponseEntity.ok(Map.of("errors", errors));
    }

    @GetMapping("/ongoing")
    public ResponseEntity<Map<String, Object>> getOngoingInterview(Authentication authentication) {
        SupabaseAuthService.SupabaseUser user = AuthSupport.requireUser(authentication);
        log.info("Checking for ongoing interview for user: {}", user.getId());

        Interview ongoingInterview = interviewService.findOngoingInterviewByUserId(user.getId());

        if (ongoingInterview == null) {
            return ResponseEntity.ok(Map.of("hasOngoingInterview", false));
        }

        Instant now = Instant.now();
        long elapsedMinutes = java.time.Duration.between(ongoingInterview.getStartedAt(), now).toMinutes();
        long remainingMinutes = ongoingInterview.getTimeMinutes() - elapsedMinutes;

        return ResponseEntity.ok(Map.of(
                "hasOngoingInterview", true,
                "interviewId", ongoingInterview.getId(),
                "startedAt", ongoingInterview.getStartedAt(),
                "difficulty", ongoingInterview.getDifficulty(),
                "numQuestions", ongoingInterview.getNumQuestions(),
                "timeMinutes", ongoingInterview.getTimeMinutes(),
                "elapsedMinutes", elapsedMinutes,
                "remainingMinutes", Math.max(0, remainingMinutes)
        ));
    }

    public static class StartInterviewRequest {
        @NotNull(message = "Difficulty is required")
        private Question.Difficulty difficulty;

        @NotNull(message = "Number of questions is required")
        @Min(value = 1, message = "Number of questions must be at least 1")
        private Integer numQuestions;

        @NotNull(message = "Time minutes is required")
        @Min(value = 1, message = "Time minutes must be at least 1")
        private Integer timeMinutes;

        public StartInterviewRequest() {}

        public Question.Difficulty getDifficulty() { return difficulty; }
        public void setDifficulty(Question.Difficulty difficulty) { this.difficulty = difficulty; }

        public Integer getNumQuestions() { return numQuestions; }
        public void setNumQuestions(Integer numQuestions) { this.numQuestions = numQuestions; }

        public Integer getTimeMinutes() { return timeMinutes; }
        public void setTimeMinutes(Integer timeMinutes) { this.timeMinutes = timeMinutes; }
    }

    public static class SubmitInterviewRequest {
        @NotEmpty(message = "At least one submission is required")
        private List<InterviewService.SubmissionRequest> submissions;

        public SubmitInterviewRequest() {}

        public List<InterviewService.SubmissionRequest> getSubmissions() { return submissions; }
        public void setSubmissions(List<InterviewService.SubmissionRequest> submissions) {
            this.submissions = submissions;
        }
    }

    public static class CheckSyntaxRequest {
        @NotBlank(message = "Code is required")
        private String code;

        @NotBlank(message = "Language is required")
        private String language;

        public CheckSyntaxRequest() {}

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
}
