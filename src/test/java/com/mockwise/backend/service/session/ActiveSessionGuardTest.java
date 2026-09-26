package com.mockwise.backend.service.session;

import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveSessionGuardTest {

    @Mock private InterviewRepository interviewRepository;
    @Mock private DesignSessionRepository designSessionRepository;
    @InjectMocks private ActiveSessionGuard guard;

    @Test
    void rejectsWhenACodingInterviewIsStillInsideItsTime() {
        when(interviewRepository.findByUserIdAndStatus("guest", Interview.Status.IN_PROGRESS))
                .thenReturn(List.of(codingStarted(Instant.now().minusSeconds(60), 45)));

        ConflictException conflict = assertThrows(ConflictException.class, () -> guard.requireClear("guest"));
        org.junit.jupiter.api.Assertions.assertTrue(
                conflict.getMessage().toLowerCase().contains("already have an interview"));
    }

    @Test
    void rejectsWhenADesignSessionIsStillInsideItsTime() {
        when(interviewRepository.findByUserIdAndStatus("guest", Interview.Status.IN_PROGRESS))
                .thenReturn(List.of());
        when(designSessionRepository.findByUserIdAndStatus("guest", SessionStatus.IN_PROGRESS))
                .thenReturn(List.of(designStarted(Instant.now().minusSeconds(60), 45)));

        assertThrows(ConflictException.class, () -> guard.requireClear("guest"));
    }

    @Test
    void allowsAStartWhenEveryOpenSessionHasRunOutOfTime() {
        when(interviewRepository.findByUserIdAndStatus("guest", Interview.Status.IN_PROGRESS))
                .thenReturn(List.of(codingStarted(Instant.now().minusSeconds(60 * 90), 45)));
        when(designSessionRepository.findByUserIdAndStatus("guest", SessionStatus.IN_PROGRESS))
                .thenReturn(List.of(designStarted(Instant.now().minusSeconds(60 * 90), 45)));

        assertDoesNotThrow(() -> guard.requireClear("guest"));
    }

    private Interview codingStarted(Instant startedAt, int minutes) {
        Interview interview = new Interview();
        interview.setStatus(Interview.Status.IN_PROGRESS);
        interview.setStartedAt(startedAt);
        interview.setTimeMinutes(minutes);
        interview.setDifficulty(Question.Difficulty.EASY);
        return interview;
    }

    private DesignSession designStarted(Instant startedAt, int minutes) {
        DesignSession session = new DesignSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(startedAt);
        session.setTimeMinutes(minutes);
        return session;
    }
}
