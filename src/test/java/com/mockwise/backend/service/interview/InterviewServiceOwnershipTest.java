package com.mockwise.backend.service.interview;

import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.exception.ForbiddenException;
import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewQuestionRepository;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.service.progress.UserQuestionSeenService;
import com.mockwise.backend.service.question.QuestionSelectionService;
import com.mockwise.backend.service.session.ActiveSessionGuard;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionRepository;
import com.mockwise.backend.repository.submission.UserSubmission;
import com.mockwise.backend.repository.submission.UserSubmissionRepository;
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
    @Mock private ActiveSessionGuard activeSessionGuard;

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

        SubmittedSolution req = new SubmittedSolution(assignedQuestionId, "code", "java", null, null);
        Interview result = interviewService.endInterview(interviewId, OWNER, List.of(req));

        assertEquals(Interview.Status.COMPLETED, result.getStatus());
        assertNotNull(result.getEndedAt());
        verify(userSubmissionRepository).saveAll(anyList());
        verify(interviewRepository).save(interview);
    }

    @Test
    void endInterview_wrongUser_throwsForbidden() {
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        SubmittedSolution req = new SubmittedSolution(assignedQuestionId, "code", "java", null, null);
        assertThrows(ForbiddenException.class,
                () -> interviewService.endInterview(interviewId, OTHER, List.of(req)));
        verify(userSubmissionRepository, never()).saveAll(anyList());
    }

    @Test
    void endInterview_notInProgress_throwsConflict() {
        interview.setStatus(Interview.Status.COMPLETED);
        when(interviewRepository.findById(interviewId)).thenReturn(Optional.of(interview));

        SubmittedSolution req = new SubmittedSolution(assignedQuestionId, "code", "java", null, null);
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

        SubmittedSolution req = new SubmittedSolution(foreignQuestionId, "code", "java", null, null);
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

        SubmittedSolution req = new SubmittedSolution(
                assignedQuestionId, "class S {}", "java", "O(n)", "O(1)");

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
