package com.mockwise.backend.controller.interview;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockwise.backend.config.AuthSupport;
import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.controller.interview.dto.GenerateFeedbackResponse;
import com.mockwise.backend.controller.interview.dto.StartInterviewRequest;
import com.mockwise.backend.controller.interview.dto.SubmissionRequest;
import com.mockwise.backend.controller.interview.dto.SubmitInterviewRequest;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.exception.GoneException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.submission.UserSubmission;
import com.mockwise.backend.service.interview.FeedbackRequestOutcome;
import com.mockwise.backend.service.interview.FeedbackService;
import com.mockwise.backend.service.interview.InterviewService;
import com.mockwise.backend.service.interview.SubmittedSolution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Slf4j
public class InterviewController {

    private final InterviewService interviewService;
    private final FeedbackService feedbackService;

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

        List<SubmittedSolution> submitted = request.getSubmissions().stream()
                .map(InterviewController::toSubmittedSolution)
                .toList();
        Interview interview = interviewService.endInterview(interviewId, user.getId(), submitted);
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

    @PostMapping("/{interviewId}/generate-feedback")
    public ResponseEntity<GenerateFeedbackResponse> generateFeedback(
            @PathVariable UUID interviewId,
            Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        // Sync gate: 403/400/READY complete on request thread before any @Async work.
        FeedbackRequestOutcome outcome =
                feedbackService.requestFeedbackGeneration(interviewId, user.getId());
        return ResponseEntity.ok(toFeedbackResponse(interviewId, outcome));
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

    @GetMapping("/test-auth")
    public ResponseEntity<?> testAuth(Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        return ResponseEntity.ok(Map.of("user", user.getEmail(), "status", "authenticated"));
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

    private static SubmittedSolution toSubmittedSolution(SubmissionRequest requested) {
        return new SubmittedSolution(
                requested.getQuestionId(),
                requested.getCode(),
                requested.getLanguage(),
                requested.getTimeComplexity(),
                requested.getSpaceComplexity());
    }

    private static GenerateFeedbackResponse toFeedbackResponse(UUID interviewId, FeedbackRequestOutcome outcome) {
        String id = interviewId.toString();
        if (outcome == FeedbackRequestOutcome.READY) {
            return GenerateFeedbackResponse.ready(id);
        }
        return GenerateFeedbackResponse.started(id);
    }
}
