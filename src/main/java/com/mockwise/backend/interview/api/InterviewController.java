package com.mockwise.backend.interview.api;

import com.mockwise.backend.auth.SupabaseUser;
import com.mockwise.backend.codesyntax.LanguageSupportService;
import com.mockwise.backend.codesyntax.SyntaxCheckService;
import com.mockwise.backend.codesyntax.model.SupportedLanguage;
import com.mockwise.backend.common.util.AuthSupport;
import com.mockwise.backend.interview.api.dto.CheckSyntaxRequest;
import com.mockwise.backend.interview.api.dto.StartInterviewRequest;
import com.mockwise.backend.interview.api.dto.SubmitInterviewRequest;
import com.mockwise.backend.interview.application.FeedbackService;
import com.mockwise.backend.interview.application.InterviewService;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.question.application.OptimalSolutionService;
import com.mockwise.backend.question.application.QuestionSelectionService;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.question.infrastructure.QuestionCodeStubRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    private final FeedbackService feedbackService;
    private final QuestionSelectionService questionSelectionService;
    private final OptimalSolutionService optimalSolutionService;
    private final SyntaxCheckService syntaxCheckService;
    private final LanguageSupportService languageSupportService;
    private final QuestionCodeStubRepository questionCodeStubRepository;

    @PostMapping("/start")
    public ResponseEntity<?> startInterview(
            @RequestBody StartInterviewRequest request,
            Authentication authentication) {

        log.info("Starting interview with request: difficulty={}, numQuestions={}, timeMinutes={}",
                request.getDifficulty(), request.getNumQuestions(), request.getTimeMinutes());

        try {
            if (request.getDifficulty() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Difficulty is required"));
            }
            if (request.getNumQuestions() == null || request.getNumQuestions() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Number of questions must be positive"));
            }
            if (request.getTimeMinutes() == null || request.getTimeMinutes() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Time minutes must be positive"));
            }

            SupabaseUser user = AuthSupport.requireUser(authentication);
            Interview interview = interviewService.startInterview(
                    user,
                    request.getDifficulty(),
                    request.getNumQuestions(),
                    request.getTimeMinutes()
            );

            List<Question> questions = interview.getAssignedQuestions();
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
    public ResponseEntity<Map<String, Object>> submitInterview(
            @PathVariable UUID interviewId,
            @RequestBody SubmitInterviewRequest request,
            Authentication authentication) {

        log.info("Submitting interview: {} with {} submissions", interviewId, request.getSubmissions().size());

        try {
            Interview interview = interviewService.endInterview(interviewId, request.getSubmissions());
            List<UUID> questionIds = request.getSubmissions().stream()
                    .map(s -> s.getQuestionId())
                    .toList();
            interviewService.markQuestionsAsSeen(interview.getUserId(), questionIds, interview.getDifficulty());

            // Async via @Async — no DB transaction held across Claude
            feedbackService.generateFeedbackForInterview(interviewId);

            return ResponseEntity.ok(Map.of(
                    "message", "Interview submitted successfully",
                    "interviewId", interview.getId().toString()
            ));
        } catch (Exception e) {
            log.error("Error submitting interview", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{interviewId}/feedback")
    public ResponseEntity<?> getInterviewFeedback(@PathVariable UUID interviewId, Authentication authentication) {
        try {
            Interview interview = interviewService.getInterviewWithFeedback(interviewId);
            List<UserSubmission> submissions = interviewService.getSubmissionsWithFeedback(interviewId);
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
            AuthSupport.requireUser(authentication);
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
            feedbackService.generateFeedbackForInterview(interviewId);
            return ResponseEntity.ok(Map.of("status", "started"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(
            @RequestParam Question.Difficulty difficulty,
            @RequestParam(defaultValue = "3") int count,
            Authentication authentication) {

        String userId = null;
        if (authentication != null) {
            try {
                userId = AuthSupport.requireUser(authentication).getId();
            } catch (Exception e) {
                log.warn("Could not extract user from authentication, using non-user-specific selection", e);
            }
        }

        List<Question> questions = questionSelectionService.getRandomQuestionsForUser(userId, difficulty, count);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{interviewId}/validate")
    public ResponseEntity<?> validateInterviewSession(
            @PathVariable UUID interviewId,
            Authentication authentication) {

        try {
            SupabaseUser user = AuthSupport.requireUser(authentication);
            Interview interview = interviewService.validateInterviewAccess(interviewId, user.getId());

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
            return ResponseEntity.status(404).body(Map.of(
                    "error", e.getMessage(),
                    "valid", false
            ));
        } catch (SecurityException e) {
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
            AuthSupport.requireUser(authentication);
            String code = optimalSolutionService.getOptimalCode(questionId, language);
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
        try {
            SupabaseUser user = AuthSupport.requireUser(authentication);
            return ResponseEntity.ok(Map.of("user", user.getEmail(), "status", "authenticated"));
        } catch (Exception e) {
            log.error("Authentication failed: ", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/check-syntax")
    public ResponseEntity<?> checkSyntax(
            @RequestBody CheckSyntaxRequest request,
            Authentication authentication) {
        log.info("Checking syntax for language: {}", request.getLanguage());
        try {
            List<String> errors = syntaxCheckService.checkSyntax(request.getCode(), request.getLanguage());
            return ResponseEntity.ok(Map.of("errors", errors));
        } catch (Exception e) {
            log.error("Error checking syntax", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/supported-languages")
    public ResponseEntity<List<SupportedLanguage>> supportedLanguages(Authentication authentication) {
        // Auth required by security config; list languages the server can attempt to check
        return ResponseEntity.ok(languageSupportService.listSupportedLanguages());
    }

    @GetMapping("/ongoing")
    public ResponseEntity<?> getOngoingInterview(Authentication authentication) {
        try {
            SupabaseUser user = AuthSupport.requireUser(authentication);
            Interview ongoingInterview = interviewService.findOngoingInterviewByUserId(user.getId());

            if (ongoingInterview != null) {
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
            return ResponseEntity.ok(Map.of("hasOngoingInterview", false));
        } catch (Exception e) {
            log.error("Error checking for ongoing interview", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to check for ongoing interview"));
        }
    }
}
