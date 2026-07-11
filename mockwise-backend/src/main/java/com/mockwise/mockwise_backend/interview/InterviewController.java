package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.SupabaseAuthService;
import com.mockwise.mockwise_backend.common.exception.BadRequestException;
import com.mockwise.mockwise_backend.common.exception.GoneException;
import com.mockwise.mockwise_backend.common.exception.ResourceNotFoundException;
import com.mockwise.mockwise_backend.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Slf4j
public class InterviewController {

    private final InterviewService interviewService;
    private final QuestionCodeStubRepository questionCodeStubRepository;

    @PostMapping("/start")
    public ResponseEntity<?> startInterview(
            @RequestBody StartInterviewRequest request,
            Authentication authentication) {

        log.info("Starting interview with request: difficulty={}, numQuestions={}, timeMinutes={}",
                request.getDifficulty(), request.getNumQuestions(), request.getTimeMinutes());

        if (request.getDifficulty() == null) {
            throw new BadRequestException("Difficulty is required.");
        }
        if (request.getNumQuestions() == null || request.getNumQuestions() <= 0) {
            throw new BadRequestException("Number of questions must be a positive value.");
        }
        if (request.getTimeMinutes() == null || request.getTimeMinutes() <= 0) {
            throw new BadRequestException("Time minutes must be a positive value.");
        }

        SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
        Interview interview = interviewService.startInterview(
                user,
                request.getDifficulty(),
                request.getNumQuestions(),
                request.getTimeMinutes()
        );

        List<Question> questions = interview.getAssignedQuestions();
        log.info("Interview created with {} assigned questions", questions.size());

        return ResponseEntity.ok(Map.of(
                "interview", interview,
                "questions", questions
        ));
    }

    @PostMapping("/{interviewId}/submit")
    public Mono<ResponseEntity<Map<String, Object>>> submitInterview(
            @PathVariable UUID interviewId,
            @RequestBody SubmitInterviewRequest request,
            Authentication authentication) {

        if (request == null || request.getSubmissions() == null) {
            throw new BadRequestException("Submissions are required.");
        }
        // Ensure caller is authenticated (ownership checks can be expanded later)
        extractSupabaseUser(authentication);

        log.info("Submitting interview: {} with {} submissions", interviewId, request.getSubmissions().size());

        Interview interview = interviewService.endInterview(interviewId, request.getSubmissions());
        interviewService.markQuestionsAsSeen(interview.getUserId(), interview);

        interviewService.generateFeedbackForInterview(interviewId)
                .doOnError(e -> log.error("Async feedback generation failed for interview: {}", interviewId, e))
                .subscribe();

        return Mono.just(ResponseEntity.ok(Map.of(
                "message", "Interview submitted successfully",
                "interviewId", interview.getId().toString()
        )));
    }

    @GetMapping("/{interviewId}/feedback")
    public ResponseEntity<?> getInterviewFeedback(@PathVariable UUID interviewId,
                                                  Authentication authentication) {
        extractSupabaseUser(authentication);
        Interview interview = interviewService.getInterviewWithFeedback(interviewId);
        List<UserSubmission> submissions = interviewService.getSubmissionsWithFeedback(interviewId);
        return ResponseEntity.ok(Map.of(
                "interview", interview,
                "submissions", submissions
        ));
    }

    @GetMapping("/questions/{questionId}/stub")
    public ResponseEntity<?> getQuestionCodeStub(
            @PathVariable UUID questionId,
            @RequestParam String language,
            Authentication authentication) {
        extractSupabaseUser(authentication);
        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        return questionCodeStubRepository
                .findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, language)
                .map(stub -> ResponseEntity.ok(Map.of("stub", stub.getStub())))
                .orElseThrow(() -> ResourceNotFoundException.of("Code stub for this language"));
    }

    @PostMapping("/{interviewId}/generate-feedback")
    public ResponseEntity<?> generateFeedback(@PathVariable UUID interviewId,
                                              Authentication authentication) {
        extractSupabaseUser(authentication);
        interviewService.generateFeedbackForInterview(interviewId)
                .doOnError(e -> log.error("Async feedback generation failed for interview: {}", interviewId, e))
                .subscribe();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Feedback generation has been started."
        ));
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(
            @RequestParam Question.Difficulty difficulty,
            @RequestParam(defaultValue = "3") int count,
            Authentication authentication) {

        if (difficulty == null) {
            throw new BadRequestException("Difficulty is required.");
        }
        if (count <= 0) {
            throw new BadRequestException("Count must be a positive value.");
        }

        String userId = null;
        try {
            userId = extractSupabaseUser(authentication).getId();
        } catch (UnauthorizedException e) {
            log.debug("Unauthenticated question list request; using non-user-specific selection");
        }

        List<Question> questions = interviewService.getRandomQuestionsForUser(userId, difficulty, count);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{interviewId}/validate")
    public ResponseEntity<?> validateInterviewSession(
            @PathVariable UUID interviewId,
            Authentication authentication) {

        SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
        Interview interview = interviewService.validateInterviewAccess(interviewId, user.getId());

        long startTime = interview.getStartedAt().toEpochMilli();
        long totalTimeMs = interview.getTimeMinutes() * 60 * 1000L;
        long remainingMs = Math.max(0, totalTimeMs - (System.currentTimeMillis() - startTime));

        if (remainingMs <= 0) {
            throw new GoneException("This interview session has ended.");
        }

        return ResponseEntity.ok(Map.of(
                "interview", interview,
                "questions", interview.getAssignedQuestions(),
                "remainingTimeMs", remainingMs,
                "valid", true
        ));
    }

    @GetMapping("/optimal-code")
    public ResponseEntity<?> getOptimalCode(
            @RequestParam UUID questionId,
            @RequestParam String language,
            Authentication authentication) {
        extractSupabaseUser(authentication);
        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        String code = interviewService.getOptimalCode(questionId, language);
        if (code == null || code.isBlank()) {
            throw ResourceNotFoundException.of("Optimal code");
        }
        return ResponseEntity.ok(Map.of("code", code));
    }

    @GetMapping("/test-auth")
    public ResponseEntity<?> testAuth(Authentication authentication) {
        SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
        return ResponseEntity.ok(Map.of("user", user.getEmail(), "status", "authenticated"));
    }

    @PostMapping("/check-syntax")
    public ResponseEntity<?> checkSyntax(
            @RequestBody CheckSyntaxRequest request,
            Authentication authentication) {
        extractSupabaseUser(authentication);
        if (request == null || request.getCode() == null) {
            throw new BadRequestException("Code is required.");
        }
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        List<String> errors = interviewService.checkSyntax(request.getCode(), request.getLanguage());
        return ResponseEntity.ok(Map.of("errors", errors));
    }

    @GetMapping("/ongoing")
    public ResponseEntity<?> getOngoingInterview(Authentication authentication) {
        SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
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

    private SupabaseAuthService.SupabaseUser extractSupabaseUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseAuthService.SupabaseUser user) {
            return user;
        }
        throw new UnauthorizedException("Authentication is required to access this resource.");
    }

    public static class StartInterviewRequest {
        private Question.Difficulty difficulty;
        private Integer numQuestions;
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
        private List<InterviewService.SubmissionRequest> submissions;

        public SubmitInterviewRequest() {}

        public List<InterviewService.SubmissionRequest> getSubmissions() { return submissions; }
        public void setSubmissions(List<InterviewService.SubmissionRequest> submissions) {
            this.submissions = submissions;
        }
    }

    public static class CheckSyntaxRequest {
        private String code;
        private String language;

        public CheckSyntaxRequest() {}

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }
}
