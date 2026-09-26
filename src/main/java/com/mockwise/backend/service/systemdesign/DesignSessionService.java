package com.mockwise.backend.service.systemdesign;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.exception.ForbiddenException;
import com.mockwise.backend.exception.GoneException;
import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.repository.systemdesign.DesignBoard;
import com.mockwise.backend.repository.systemdesign.DesignBoardRepository;
import com.mockwise.backend.repository.systemdesign.DesignPrompt;
import com.mockwise.backend.repository.systemdesign.DesignPromptRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import com.mockwise.backend.service.session.ActiveSessionGuard;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DesignSessionService {

    private final DesignPromptRepository promptRepository;
    private final DesignSessionRepository sessionRepository;
    private final DesignBoardRepository boardRepository;
    private final DesignReviewer reviewer;
    private final DesignSubmitStore submitStore;
    private final ActiveSessionGuard activeSessionGuard;

    @Transactional
    public DesignSession start(SupabaseUser user, Difficulty level, int minutes) {
        if (level == null) {
            throw new BadRequestException("Difficulty is required.");
        }
        if (minutes <= 0) {
            throw new BadRequestException("Time minutes must be a positive value.");
        }
        for (DesignSession open : sessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.IN_PROGRESS)) {
            if (stillOpen(open)) {
                continue;
            }
            open.setStatus(SessionStatus.COMPLETED);
            open.setEndedAt(Instant.now());
            sessionRepository.save(open);
        }
        activeSessionGuard.requireClear(user.getId());

        List<DesignPrompt> choices = promptRepository.findByLevel(level);
        if (choices.isEmpty()) {
            throw new ResourceNotFoundException("No system design prompt is available for this difficulty.");
        }
        DesignPrompt chosen = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));

        DesignSession session = new DesignSession();
        session.setUserId(user.getId());
        session.setUserEmail(user.getEmail());
        session.setLevel(level);
        session.setTimeMinutes(minutes);
        session.setPrompt(chosen);
        session.setStartedAt(Instant.now());
        session.setStatus(SessionStatus.IN_PROGRESS);
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public DesignSession requireOpen(UUID sessionKey, String userId) {
        DesignSession session = requireOwned(sessionKey, userId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new ConflictException("This design session is already finished.");
        }
        if (!stillOpen(session)) {
            throw new GoneException("This design session has ended.");
        }
        return session;
    }

    public DesignSession submit(UUID sessionKey, String userId, String scene, String caption) {
        DesignSession session = requireOwned(sessionKey, userId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new ConflictException("This design session is already finished.");
        }
        String drawing = scene == null ? "" : scene;
        DesignReviewer.Review review = reviewer.review(session.getPrompt(), drawing, caption);
        return submitStore.finish(sessionKey, userId, drawing, caption, review);
    }

    @Transactional(readOnly = true)
    public DesignBoard feedback(UUID sessionKey, String userId) {
        DesignSession session = requireOwned(sessionKey, userId);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new ConflictException("Feedback is available after the session is submitted.");
        }
        return boardRepository.findBySession_Id(sessionKey)
                .orElseThrow(() -> ResourceNotFoundException.of("Design submission"));
    }

    private DesignSession requireOwned(UUID sessionKey, String userId) {
        DesignSession session = sessionRepository.findById(sessionKey)
                .orElseThrow(() -> ResourceNotFoundException.of("Design session"));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this design session.");
        }
        return session;
    }

    private boolean stillOpen(DesignSession session) {
        long elapsedMinutes = java.time.Duration.between(session.getStartedAt(), Instant.now()).toMinutes();
        return elapsedMinutes < session.getTimeMinutes();
    }
}
