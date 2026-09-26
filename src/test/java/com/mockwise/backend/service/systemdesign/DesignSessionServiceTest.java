package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.service.dashboard.PracticeLedger;
import com.mockwise.backend.repository.systemdesign.DesignBoardRepository;
import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.repository.systemdesign.DesignPromptRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.systemdesign.Level;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignSessionServiceTest {

    @Mock private DesignPromptRepository promptRepository;
    @Mock private DesignSessionRepository sessionRepository;
    @Mock private DesignBoardRepository boardRepository;
    @Mock private DesignReviewer reviewer;
    @Mock private PracticeLedger practiceLedger;
    @InjectMocks private DesignSessionService designSessionService;

    @Test
    void startAssignsOnePrompt() {
        DesignPrompt prompt = new DesignPrompt();
        prompt.setTitle("URL Shortener");
        prompt.setLevel(Level.EASY);
        when(sessionRepository.findFirstByUserIdAndStatus("guest", SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(promptRepository.findByLevel(Level.EASY)).thenReturn(List.of(prompt));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            DesignSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        DesignSession started = designSessionService.start(new SupabaseUser("guest", "test@mockwise.local", true), Level.EASY, 45);

        assertEquals(prompt, started.getPrompt());
        assertEquals(SessionStatus.IN_PROGRESS, started.getStatus());
        assertEquals(45, started.getTimeMinutes());
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
        when(sessionRepository.findById(sessionKey)).thenReturn(Optional.of(session));
        when(reviewer.review("[]")).thenReturn(new DesignReviewer.Review("{}", 4));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DesignSession finished = designSessionService.submit(sessionKey, "guest", "[]", "notes");

        assertEquals(SessionStatus.COMPLETED, finished.getStatus());
        assertEquals(4.0, finished.getOverallRating());
        verify(boardRepository).save(any());
    }
}
