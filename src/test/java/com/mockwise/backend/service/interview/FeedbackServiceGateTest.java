package com.mockwise.backend.service.interview;

import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.exception.ForbiddenException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.submission.UserSubmission;
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
        interview.setDifficulty(Difficulty.EASY);
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

        FeedbackRequestOutcome outcome = feedbackService.requestFeedbackGeneration(interviewId, OWNER);

        assertEquals(FeedbackRequestOutcome.READY, outcome);
        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void requestFeedbackGeneration_pending_returnsStarted_andSchedulesWorker() {
        when(interviewService.requireOwnedInterview(interviewId, OWNER)).thenReturn(interview);
        UserSubmission pending = submissionWithFeedback(null);
        when(feedbackPersistence.loadSubmissionsWithQuestions(interviewId)).thenReturn(List.of(pending));

        FeedbackRequestOutcome outcome = feedbackService.requestFeedbackGeneration(interviewId, OWNER);

        assertEquals(FeedbackRequestOutcome.STARTED, outcome);
        verify(feedbackGenerationWorker, times(1)).runFeedbackGeneration(interviewId);
    }

    @Test
    void requestFeedbackGeneration_softFailed_requeuesWorker() {
        interview.setEndedAt(Instant.now().minus(15, ChronoUnit.MINUTES));
        when(interviewService.requireOwnedInterview(interviewId, OWNER)).thenReturn(interview);
        UserSubmission incomplete = submissionWithFeedback(null);
        when(feedbackPersistence.loadSubmissionsWithQuestions(interviewId)).thenReturn(List.of(incomplete));

        FeedbackRequestOutcome outcome = feedbackService.requestFeedbackGeneration(interviewId, OWNER);

        assertEquals(FeedbackRequestOutcome.STARTED, outcome);
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
        s.setLanguage(com.mockwise.backend.repository.question.ProgrammingLanguage.JAVA);
        s.setSubmittedAt(Instant.now());
        s.setClaudeFeedback(feedback);
        if (feedback != null) {
            s.setFeedbackGeneratedAt(Instant.now());
        }
        return s;
    }
}
