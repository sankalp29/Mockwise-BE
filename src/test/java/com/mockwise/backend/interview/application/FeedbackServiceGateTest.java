package com.mockwise.backend.interview.application;

import com.mockwise.backend.common.exception.BadRequestException;
import com.mockwise.backend.common.exception.ForbiddenException;
import com.mockwise.backend.interview.api.dto.GenerateFeedbackResponse;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.submission.domain.UserSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceGateTest {

    private static final String OWNER = "user-owner";
    private static final String OTHER = "user-other";

    @Mock private InterviewService interviewService;
    @Mock private FeedbackPersistence feedbackPersistence;
    @Mock private FeedbackGenerationWorker feedbackGenerationWorker;

    @InjectMocks
    private FeedbackService feedbackService;

    private UUID interviewId;
    private Interview interview;

    @BeforeEach
    void setUp() {
        interviewId = UUID.randomUUID();
        interview = new Interview();
        interview.setId(interviewId);
        interview.setUserId(OWNER);
        interview.setUserEmail("owner@test.com");
        interview.setDifficulty(Question.Difficulty.EASY);
        interview.setNumQuestions(1);
        interview.setTimeMinutes(30);
        interview.setStartedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        interview.setEndedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        interview.setStatus(Interview.Status.COMPLETED);
    }

    @Test
    void requestFeedbackGeneration_wrongUser_throwsForbidden_andDoesNotScheduleWorker() {
        when(interviewService.requireOwnedInterview(interviewId, OTHER))
                .thenThrow(new ForbiddenException("You do not have access to this interview session."));

        assertThrows(ForbiddenException.class,
                () -> feedbackService.requestFeedbackGeneration(interviewId, OTHER));

        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
        verify(feedbackPersistence, never()).loadSubmissionsWithQuestions(any());
    }

    @Test
    void requestFeedbackGeneration_zeroSubmissions_throwsBadRequest() {
        when(interviewService.requireOwnedInterview(interviewId, OWNER)).thenReturn(interview);
        when(feedbackPersistence.loadSubmissionsWithQuestions(interviewId)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> feedbackService.requestFeedbackGeneration(interviewId, OWNER));

        assertTrue(ex.getClientMessage().toLowerCase().contains("nothing to evaluate")
                || ex.getClientMessage().toLowerCase().contains("no submissions"));
        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void requestFeedbackGeneration_ready_returnsAlreadyReady_withoutWorker() {
        when(interviewService.requireOwnedInterview(interviewId, OWNER)).thenReturn(interview);
        UserSubmission ready = submissionWithFeedback("{\"overallRating\":8}");
        when(feedbackPersistence.loadSubmissionsWithQuestions(interviewId)).thenReturn(List.of(ready));

        GenerateFeedbackResponse response = feedbackService.requestFeedbackGeneration(interviewId, OWNER);

        assertEquals("ALREADY_READY", response.statusCode());
        assertEquals("started", response.status());
        assertEquals(interviewId.toString(), response.interviewId());
        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void requestFeedbackGeneration_pending_returnsStarted_andSchedulesWorker() {
        when(interviewService.requireOwnedInterview(interviewId, OWNER)).thenReturn(interview);
        UserSubmission pending = submissionWithFeedback(null);
        when(feedbackPersistence.loadSubmissionsWithQuestions(interviewId)).thenReturn(List.of(pending));

        GenerateFeedbackResponse response = feedbackService.requestFeedbackGeneration(interviewId, OWNER);

        assertEquals("STARTED", response.statusCode());
        assertEquals("started", response.status());
        verify(feedbackGenerationWorker, times(1)).runFeedbackGeneration(interviewId);
    }

    @Test
    void requestFeedbackGeneration_softFailed_requeuesWorker() {
        interview.setEndedAt(Instant.now().minus(15, ChronoUnit.MINUTES));
        when(interviewService.requireOwnedInterview(interviewId, OWNER)).thenReturn(interview);
        UserSubmission incomplete = submissionWithFeedback(null);
        when(feedbackPersistence.loadSubmissionsWithQuestions(interviewId)).thenReturn(List.of(incomplete));

        GenerateFeedbackResponse response = feedbackService.requestFeedbackGeneration(interviewId, OWNER);

        assertEquals("STARTED", response.statusCode());
        verify(feedbackGenerationWorker, times(1)).runFeedbackGeneration(interviewId);
    }

    @Test
    void runFeedbackGeneration_delegatesToWorker() {
        feedbackService.runFeedbackGeneration(interviewId);
        verify(feedbackGenerationWorker).runFeedbackGeneration(interviewId);
    }

    private UserSubmission submissionWithFeedback(String feedback) {
        UserSubmission s = new UserSubmission();
        s.setId(UUID.randomUUID());
        s.setInterview(interview);
        s.setCode("code");
        s.setLanguage("java");
        s.setSubmittedAt(Instant.now());
        s.setClaudeFeedback(feedback);
        if (feedback != null) {
            s.setFeedbackGeneratedAt(Instant.now());
        }
        return s;
    }
}
