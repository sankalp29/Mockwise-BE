package com.mockwise.backend.interview.application;

import com.mockwise.backend.common.exception.BadRequestException;
import com.mockwise.backend.common.exception.ConflictException;
import com.mockwise.backend.common.exception.ForbiddenException;
import com.mockwise.backend.common.exception.ResourceNotFoundException;
import com.mockwise.backend.interview.api.dto.SubmissionRequest;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.infrastructure.InterviewQuestionRepository;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.progress.application.UserQuestionSeenService;
import com.mockwise.backend.question.application.QuestionSelectionService;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.question.infrastructure.QuestionRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewServiceOwnershipTest {

    private static final String OWNER = "user-owner";
    private static final String OTHER = "user-other";

    @Mock private InterviewRepository interviewRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private UserSubmissionRepository userSubmissionRepository;
    @Mock private QuestionSelectionService questionSelectionService;
    @Mock private UserQuestionSeenService userQuestionSeenService;
    @Mock private InterviewQuestionRepository interviewQuestionRepository;

    @InjectMocks
    private InterviewService interviewService;

    private UUID interviewId;
    private UUID assignedQuestionId;
    private Interview interview;
    private Question assignedQuestion;

    @BeforeEach
    void setUp() {
        interviewId = UUID.randomUUID();
        assignedQuestionId = UUID.randomUUID();

        assignedQuestion = new Question();
        assignedQuestion.setId(assignedQuestionId);
        assignedQuestion.setTitle("Two Sum");
        assignedQuestion.setDescription("desc");
        assignedQuestion.setDifficulty(Question.Difficulty.EASY);

        interview = new Interview();
        interview.setId(interviewId);
        interview.setUserId(OWNER);
        interview.setUserEmail("owner@test.com");
        interview.setDifficulty(Question.Difficulty.EASY);
        interview.setNumQuestions(1);
        interview.setTimeMinutes(30);
        interview.setStartedAt(Instant.now().minusSeconds(60));
        interview.setStatus(Interview.Status.IN_PROGRESS);
    }

    @Test
    void requireOwnedInterview_owner_returnsInterview() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        Interview result = interviewService.requireOwnedInterview(interviewId, OWNER);

        assertSame(interview, result);
    }

    @Test
    void requireOwnedInterview_wrongUser_throwsForbidden() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        assertThrows(ForbiddenException.class,
                () -> interviewService.requireOwnedInterview(interviewId, OTHER));
    }

    @Test
    void requireOwnedInterview_missing_throwsNotFound() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> interviewService.requireOwnedInterview(interviewId, OWNER));
    }

    @Test
    void endInterview_owner_success_completesAndSaves() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));
        when(interviewQuestionRepository.findQuestionIdsByInterviewId(interviewId))
                .thenReturn(List.of(assignedQuestionId));
        when(questionRepository.findAllById(anyList())).thenReturn(List.of(assignedQuestion));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSubmissionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SubmissionRequest req = new SubmissionRequest(assignedQuestionId, "code", "java");
        Interview result = interviewService.endInterview(interviewId, OWNER, List.of(req));

        assertEquals(Interview.Status.COMPLETED, result.getStatus());
        assertNotNull(result.getEndedAt());
        verify(userSubmissionRepository).saveAll(anyList());
        verify(interviewRepository).save(interview);
    }

    @Test
    void endInterview_wrongUser_throwsForbidden() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        SubmissionRequest req = new SubmissionRequest(assignedQuestionId, "code", "java");
        assertThrows(ForbiddenException.class,
                () -> interviewService.endInterview(interviewId, OTHER, List.of(req)));
        verify(userSubmissionRepository, never()).saveAll(anyList());
    }

    @Test
    void endInterview_notInProgress_throwsConflict() {
        interview.setStatus(Interview.Status.COMPLETED);
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        SubmissionRequest req = new SubmissionRequest(assignedQuestionId, "code", "java");
        assertThrows(ConflictException.class,
                () -> interviewService.endInterview(interviewId, OWNER, List.of(req)));
        verify(userSubmissionRepository, never()).saveAll(anyList());
    }

    @Test
    void endInterview_unassignedQuestion_throwsBadRequest() {
        UUID foreignQuestionId = UUID.randomUUID();
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));
        when(interviewQuestionRepository.findQuestionIdsByInterviewId(interviewId))
                .thenReturn(List.of(assignedQuestionId));

        SubmissionRequest req = new SubmissionRequest(foreignQuestionId, "code", "java");
        assertThrows(BadRequestException.class,
                () -> interviewService.endInterview(interviewId, OWNER, List.of(req)));
        verify(userSubmissionRepository, never()).saveAll(anyList());
    }

    @Test
    void getInterviewWithFeedback_wrongUser_throwsForbidden() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        assertThrows(ForbiddenException.class,
                () -> interviewService.getInterviewWithFeedback(interviewId, OTHER));
    }

    @Test
    void getInterviewWithFeedback_owner_returnsInterview() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        Interview result = interviewService.getInterviewWithFeedback(interviewId, OWNER);
        assertSame(interview, result);
    }

    @Test
    void getSubmissionsWithFeedback_wrongUser_throwsForbidden() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        assertThrows(ForbiddenException.class,
                () -> interviewService.getSubmissionsWithFeedback(interviewId, OTHER));
        verify(userSubmissionRepository, never()).findByInterviewIdWithQuestionOrderBySubmittedAt(any());
    }

    @Test
    void getSubmissionsWithFeedback_owner_returnsList() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));
        UserSubmission sub = new UserSubmission();
        when(userSubmissionRepository.findByInterviewIdWithQuestionOrderBySubmittedAt(interviewId))
                .thenReturn(List.of(sub));

        List<UserSubmission> result = interviewService.getSubmissionsWithFeedback(interviewId, OWNER);
        assertEquals(1, result.size());
    }

    @Test
    void validateInterviewAccess_delegatesToRequireOwned() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        Interview result = interviewService.validateInterviewAccess(interviewId, OWNER);
        assertSame(interview, result);

        assertThrows(ForbiddenException.class,
                () -> interviewService.validateInterviewAccess(interviewId, OTHER));
    }

    @Test
    void endInterview_persistsSubmissionFields() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));
        when(interviewQuestionRepository.findQuestionIdsByInterviewId(interviewId))
                .thenReturn(List.of(assignedQuestionId));
        when(questionRepository.findAllById(anyList())).thenReturn(List.of(assignedQuestion));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSubmissionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SubmissionRequest req = new SubmissionRequest();
        req.setQuestionId(assignedQuestionId);
        req.setCode("class S {}");
        req.setLanguage("java");
        req.setTimeComplexity("O(n)");
        req.setSpaceComplexity("O(1)");

        interviewService.endInterview(interviewId, OWNER, List.of(req));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserSubmission>> captor = ArgumentCaptor.forClass(List.class);
        verify(userSubmissionRepository).saveAll(captor.capture());
        UserSubmission saved = captor.getValue().get(0);
        assertEquals("class S {}", saved.getCode());
        assertEquals("java", saved.getLanguage());
        assertEquals("O(n)", saved.getUserTimeComplexity());
        assertEquals("O(1)", saved.getUserSpaceComplexity());
        assertEquals(assignedQuestion, saved.getQuestion());
    }
}
