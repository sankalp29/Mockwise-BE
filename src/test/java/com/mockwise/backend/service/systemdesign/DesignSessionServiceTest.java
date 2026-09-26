package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.service.session.ActiveSessionGuard;
import com.mockwise.backend.repository.systemdesign.DesignBoardRepository;
import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.repository.systemdesign.DesignPromptRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignSessionServiceTest {

    @Mock private DesignPromptRepository promptRepository;
    @Mock private DesignSessionRepository sessionRepository;
    @Mock private DesignBoardRepository boardRepository;
    @Mock private DesignReviewer reviewer;
    @Mock private DesignSubmitStore submitStore;
    @Mock private ActiveSessionGuard activeSessionGuard;
    @InjectMocks private DesignSessionService designSessionService;

    @Test
    void startAssignsOnePrompt() {
        DesignPrompt prompt = new DesignPrompt();
        prompt.setTitle("URL Shortener");
        prompt.setLevel(Difficulty.EASY);
        when(sessionRepository.findByUserIdAndStatus("guest", SessionStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(promptRepository.findByLevel(Difficulty.EASY)).thenReturn(List.of(prompt));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            DesignSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        DesignSession started = designSessionService.start(new SupabaseUser("guest", "test@mockwise.local", true), Difficulty.EASY, 45);

        assertEquals(prompt, started.getPrompt());
        assertEquals(SessionStatus.IN_PROGRESS, started.getStatus());
        assertEquals(45, started.getTimeMinutes());
        verify(activeSessionGuard).requireClear("guest");
    }

    @Test
    void startDoesNotCreateASessionWhenAnotherInterviewIsOpen() {
        when(sessionRepository.findByUserIdAndStatus("guest", SessionStatus.IN_PROGRESS))
                .thenReturn(List.of());
        doThrow(new ConflictException(ActiveSessionGuard.ALREADY_IN_PROGRESS))
                .when(activeSessionGuard).requireClear("guest");

        assertThrows(ConflictException.class, () ->
                designSessionService.start(new SupabaseUser("guest", "test@mockwise.local", true), Difficulty.EASY, 45));

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void submitStoresTheDrawingAndFinishesTheSession() {
        UUID sessionKey = UUID.randomUUID();
        DesignSession session = new DesignSession();
        session.setId(sessionKey);
        session.setUserId("guest");
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(java.time.Instant.now());
        session.setTimeMinutes(45);
        session.setPrompt(new DesignPrompt());
        when(sessionRepository.findById(sessionKey)).thenReturn(Optional.of(session));
        when(reviewer.review(any(), eq("[]"), eq("notes"))).thenReturn(new DesignReviewer.Review("{}", 4));
        when(submitStore.finish(eq(sessionKey), eq("guest"), eq("[]"), eq("notes"), any()))
                .thenAnswer(invocation -> {
                    session.setStatus(SessionStatus.COMPLETED);
                    session.setOverallRating(4.0);
                    return session;
                });

        DesignSession finished = designSessionService.submit(sessionKey, "guest", "[]", "notes");

        assertEquals(SessionStatus.COMPLETED, finished.getStatus());
        assertEquals(4.0, finished.getOverallRating());
        verify(submitStore).finish(eq(sessionKey), eq("guest"), eq("[]"), eq("notes"), any());
    }
}
