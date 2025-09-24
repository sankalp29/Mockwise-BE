package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.SupabaseAuthService;
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
        log.info("Authentication: {}", authentication);
        
        try {
            // Validate request
            if (request.getDifficulty() == null) {
                log.error("Difficulty is null in request");
                return ResponseEntity.badRequest().body(Map.of("error", "Difficulty is required"));
            }
            if (request.getNumQuestions() == null || request.getNumQuestions() <= 0) {
                log.error("Invalid numQuestions: {}", request.getNumQuestions());
                return ResponseEntity.badRequest().body(Map.of("error", "Number of questions must be positive"));
            }
            if (request.getTimeMinutes() == null || request.getTimeMinutes() <= 0) {
                log.error("Invalid timeMinutes: {}", request.getTimeMinutes());
                return ResponseEntity.badRequest().body(Map.of("error", "Time minutes must be positive"));
            }
            
            // Get Supabase user from authentication
            SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
            log.info("User extracted successfully: {}", user.getEmail());
            
            Interview interview = interviewService.startInterview(
                user, 
                request.getDifficulty(), 
                request.getNumQuestions(), 
                request.getTimeMinutes()
            );
            
            // Get the assigned questions from the interview
            List<Question> questions = interview.getAssignedQuestions();
            
            log.info("Interview created with {} assigned questions for difficulty: {}", 
                     questions.size(), request.getDifficulty());
            
            return ResponseEntity.ok(Map.of(
                "interview", interview,
                "questions", questions
            ));
        } catch (Exception e) {
            log.error("Error starting interview", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{interviewId}/submit")
    public Mono<ResponseEntity<Map<String, Object>>> submitInterview(
            @PathVariable UUID interviewId,
            @RequestBody SubmitInterviewRequest request,
            Authentication authentication) {
        
        log.info("Submitting interview: {} with {} submissions", interviewId, request.getSubmissions().size());
        log.info("Authentication present: {}", authentication != null);
        log.info("Authentication type: {}", authentication != null ? authentication.getClass().getSimpleName() : "null");
        
        try {
            // End the interview and save submissions
            Interview interview = interviewService.endInterview(interviewId, request.getSubmissions());
            log.info("Interview ended successfully: {}", interview.getId());
            log.info("Interview ended: {}", interview);


            // Generate feedback asynchronously (fire-and-forget)
            log.info("*****STARTING FEEDBACK GENERATION******");
            interviewService.generateFeedbackForInterview(interviewId)
                    .doOnError(e -> log.error("Async feedback generation failed for interview: {}", interviewId, e))
                    .subscribe(); // Fire-and-forget

            // Return immediate success response
            Map<String, Object> response = Map.of(
                "message", "Interview submitted successfully",
                "interviewId", interview.getId().toString()
            );
            return Mono.just(ResponseEntity.ok(response));
                    
        } catch (Exception e) {
            log.error("Error submitting interview", e);
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", (Object) e.getMessage())));
        }
    }

    @GetMapping("/{interviewId}/feedback")
    public ResponseEntity<?> getInterviewFeedback(@PathVariable UUID interviewId, Authentication authentication) {
        try {
            log.info("Getting interview feedback for interview: {}", interviewId);
            log.info("Authentication Info: {}", authentication);
            Interview interview = interviewService.getInterviewWithFeedback(interviewId);
            
            log.info("Getting submissions with feedback for interview: {}", interviewId);
            List<UserSubmission> submissions = interviewService.getSubmissionsWithFeedback(interviewId);
            log.info("Submissions with feedback: {}", submissions);
            
            return ResponseEntity.ok(Map.of(
                "interview", interview,
                "submissions", submissions
            ));
        } catch (Exception e) {
            log.error("Error getting interview feedback", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/questions/{questionId}/stub")
    public ResponseEntity<String> getQuestionCodeStub(
            @PathVariable UUID questionId,
            @RequestParam String language,
            Authentication authentication) {
        try {
            // Ensure user is authenticated
            extractSupabaseUser(authentication);
            return questionCodeStubRepository
                    .findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, language)
                    .map(stub -> ResponseEntity.ok(stub.getStub()))
                    .orElseGet(() -> ResponseEntity.status(404).body("// No code stub available for this language"));
        } catch (Exception e) {
            log.error("Error fetching code stub for question {} and language {}", questionId, language, e);
            return ResponseEntity.status(500).body("// Error fetching stub");
        }
    }

    @PostMapping("/{interviewId}/generate-feedback")
    public ResponseEntity<?> generateFeedback(@PathVariable UUID interviewId) {
        try {
            interviewService.generateFeedbackForInterview(interviewId).subscribe();
            return ResponseEntity.ok(Map.of("status", "started"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(
            @RequestParam Question.Difficulty difficulty,
            @RequestParam(defaultValue = "3") int count) {
        
        List<Question> questions = interviewService.getRandomQuestions(difficulty, count);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{interviewId}/validate")
    public ResponseEntity<?> validateInterviewSession(
            @PathVariable UUID interviewId,
            Authentication authentication) {
        
        try {
            log.info("Validating interview session: {}", interviewId);
            
            // Get Supabase user from authentication
            SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
            log.info("User extracted for validation: {}", user.getEmail());
            
            Interview interview = interviewService.validateInterviewAccess(interviewId, user.getId());
            
            // Calculate remaining time
            long startTime = interview.getStartedAt().toEpochMilli();
            long totalTimeMs = interview.getTimeMinutes() * 60 * 1000L;
            long currentTime = System.currentTimeMillis();
            long elapsedMs = currentTime - startTime;
            long remainingMs = Math.max(0, totalTimeMs - elapsedMs);
            
            if (remainingMs <= 0) {
                return ResponseEntity.status(410).body(Map.of(
                    "error", "Interview has ended",
                    "expired", true
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "interview", interview,
                "questions", interview.getAssignedQuestions(),
                "remainingTimeMs", remainingMs,
                "valid", true
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Interview validation failed: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                "error", e.getMessage(),
                "valid", false
            ));
        } catch (SecurityException e) {
            log.warn("Unauthorized interview access attempt: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of(
                "error", "You don't have access to this interview session",
                "valid", false
            ));
        } catch (Exception e) {
            log.error("Error validating interview session", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Internal server error",
                "valid", false
            ));
        }
    }

    @GetMapping("/optimal-code")
    public ResponseEntity<?> getOptimalCode(
            @RequestParam UUID questionId,
            @RequestParam String language,
            Authentication authentication) {
        try {
            log.info("Fetching optimal code for questionId: {}, language: {}", questionId, language);
            // Ensure user is authenticated
            extractSupabaseUser(authentication);
            String code = interviewService.getOptimalCode(questionId, language);
            if (code == null || code.isBlank()) {
                return ResponseEntity.status(404).body(Map.of("error", "Optimal code not found"));
            }
            return ResponseEntity.ok(Map.of("code", code));
        } catch (Exception e) {
            log.error("Error fetching optimal code", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/test-auth")
    public ResponseEntity<?> testAuth(Authentication authentication) {
        log.info("Testing authentication...");
        log.info("Authentication: {}", authentication);
        
        try {
            SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
            log.info("User found: {}", user.getEmail());
            return ResponseEntity.ok(Map.of("user", user.getEmail(), "status", "authenticated"));
        } catch (Exception e) {
            log.error("Authentication failed: ", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private SupabaseAuthService.SupabaseUser extractSupabaseUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No authentication found");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseAuthService.SupabaseUser user) {
            return user;
        }
        
        throw new IllegalArgumentException("Invalid authentication type: " + principal.getClass().getSimpleName());
    }

    // Request DTOs
    public static class StartInterviewRequest {
        private Question.Difficulty difficulty;
        private Integer numQuestions;
        private Integer timeMinutes;

        // Constructors, getters, setters
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
        public void setSubmissions(List<InterviewService.SubmissionRequest> submissions) { this.submissions = submissions; }
    }

    @PostMapping("/check-syntax")
    public ResponseEntity<?> checkSyntax(
            @RequestBody CheckSyntaxRequest request,
            Authentication authentication) {
        log.info("Checking syntax for language: {}", request.getLanguage());
        try {
            List<String> errors = interviewService.checkSyntax(request.getCode(), request.getLanguage());
            return ResponseEntity.ok(Map.of("errors", errors));
        } catch (Exception e) {
            log.error("Error checking syntax", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/ongoing")
    public ResponseEntity<?> getOngoingInterview(Authentication authentication) {
        try {
            SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
            log.info("Checking for ongoing interview for user: {}", user.getEmail());
            
            Interview ongoingInterview = interviewService.findOngoingInterviewByUserId(user.getId());
            
            if (ongoingInterview != null) {
                // Calculate remaining time
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
            } else {
                return ResponseEntity.ok(Map.of("hasOngoingInterview", false));
            }
        } catch (Exception e) {
            log.error("Error checking for ongoing interview", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to check for ongoing interview"));
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
