package com.mockwise.backend.interview.api;

import com.mockwise.backend.auth.SupabaseUser;
import com.mockwise.backend.codesyntax.LanguageSupportService;
import com.mockwise.backend.codesyntax.SyntaxCheckService;
import com.mockwise.backend.codesyntax.model.SupportedLanguage;
import com.mockwise.backend.common.exception.BadRequestException;
import com.mockwise.backend.common.exception.GoneException;
import com.mockwise.backend.common.exception.ResourceNotFoundException;
import com.mockwise.backend.common.exception.UnauthorizedException;
import com.mockwise.backend.common.util.AuthSupport;
import com.mockwise.backend.interview.api.dto.CheckSyntaxRequest;
import com.mockwise.backend.interview.api.dto.GenerateFeedbackResponse;
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

        if (request.getDifficulty() == null) {
            throw new BadRequestException("Difficulty is required.");
        }
        if (request.getNumQuestions() == null || request.getNumQuestions() <= 0) {
            throw new BadRequestException("Number of questions must be a positive value.");
        }
        if (request.getTimeMinutes() == null || request.getTimeMinutes() <= 0) {
            throw new BadRequestException("Time minutes must be a positive value.");
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
    }

    @PostMapping("/{interviewId}/submit")
    public ResponseEntity<Map<String, Object>> submitInterview(
            @PathVariable UUID interviewId,
            @RequestBody SubmitInterviewRequest request,
            Authentication authentication) {

        if (request == null || request.getSubmissions() == null) {
            throw new BadRequestException("Submissions are required.");
        }
        SupabaseUser user = AuthSupport.requireUser(authentication);

        log.info("Submitting interview: {} with {} submissions", interviewId, request.getSubmissions().size());

        Interview interview = interviewService.endInterview(interviewId, user.getId(), request.getSubmissions());
        List<UUID> questionIds = request.getSubmissions().stream()
                .map(s -> s.getQuestionId())
                .toList();
        interviewService.markQuestionsAsSeen(interview.getUserId(), questionIds, interview.getDifficulty());

        // Ownership already proven by endInterview; fire async worker only (no re-gate).
        feedbackService.runFeedbackGeneration(interviewId);

        return ResponseEntity.ok(Map.of(
                "message", "Interview submitted successfully",
                "interviewId", interview.getId().toString()
        ));
    }

    @GetMapping("/{interviewId}/feedback")
    public ResponseEntity<?> getInterviewFeedback(@PathVariable UUID interviewId,
                                                  Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        Interview interview = interviewService.getInterviewWithFeedback(interviewId, user.getId());
        List<UserSubmission> submissions =
                interviewService.getSubmissionsWithFeedback(interviewId, user.getId());
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
        AuthSupport.requireUser(authentication);
        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        return questionCodeStubRepository
                .findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, language)
                .map(stub -> ResponseEntity.ok(Map.of("stub", stub.getStub())))
                .orElseThrow(() -> ResourceNotFoundException.of("Code stub for this language"));
    }

    @PostMapping("/{interviewId}/generate-feedback")
    public ResponseEntity<GenerateFeedbackResponse> generateFeedback(
            @PathVariable UUID interviewId,
            Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        // Sync gate: 403/400/ALREADY_READY complete on request thread before any @Async work.
        GenerateFeedbackResponse response =
                feedbackService.requestFeedbackGeneration(interviewId, user.getId());
        return ResponseEntity.ok(response);
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
            userId = AuthSupport.requireUser(authentication).getId();
        } catch (UnauthorizedException | IllegalArgumentException e) {
            log.debug("Using non-user-specific question selection");
        }

        List<Question> questions = questionSelectionService.getRandomQuestionsForUser(userId, difficulty, count);
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{interviewId}/validate")
    public ResponseEntity<?> validateInterviewSession(
            @PathVariable UUID interviewId,
            Authentication authentication) {

        SupabaseUser user = AuthSupport.requireUser(authentication);
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
        AuthSupport.requireUser(authentication);
        if (language == null || language.isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        String code = optimalSolutionService.getOptimalCode(questionId, language);
        if (code == null || code.isBlank()) {
            throw ResourceNotFoundException.of("Optimal code");
        }
        return ResponseEntity.ok(Map.of("code", code));
    }

    @GetMapping("/test-auth")
    public ResponseEntity<?> testAuth(Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(Map.of("user", user.getEmail(), "status", "authenticated"));
    }

    @PostMapping("/check-syntax")
    public ResponseEntity<?> checkSyntax(
            @RequestBody CheckSyntaxRequest request,
            Authentication authentication) {
        AuthSupport.requireUser(authentication);
        if (request == null || request.getCode() == null) {
            throw new BadRequestException("Code is required.");
        }
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new BadRequestException("Language is required.");
        }
        List<String> errors = syntaxCheckService.checkSyntax(request.getCode(), request.getLanguage());
        return ResponseEntity.ok(Map.of("errors", errors));
    }

    @GetMapping("/supported-languages")
    public ResponseEntity<List<SupportedLanguage>> supportedLanguages(Authentication authentication) {
        AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(languageSupportService.listSupportedLanguages());
    }

    @GetMapping("/ongoing")
    public ResponseEntity<?> getOngoingInterview(Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
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
}
