package com.mockwise.backend.service.systemdesign;

import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.exception.ForbiddenException;
import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.repository.systemdesign.DesignBoard;
import com.mockwise.backend.repository.systemdesign.DesignBoardRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import com.mockwise.backend.service.dashboard.PracticeLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes the finished design session. The model call stays outside this transaction.
 */
@Service
@RequiredArgsConstructor
public class DesignSubmitStore {

    private final DesignSessionRepository sessionRepository;
    private final DesignBoardRepository boardRepository;
    private final PracticeLedger practiceLedger;

    @Transactional
    public DesignSession finish(UUID sessionKey, String userId, String scene, String caption,
                                DesignReviewer.Review review) {
        DesignSession session = sessionRepository.findById(sessionKey)
                .orElseThrow(() -> ResourceNotFoundException.of("Design session"));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this design session.");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new ConflictException("This design session is already finished.");
        }

        DesignBoard board = new DesignBoard();
        board.setSession(session);
        board.setScene(scene == null ? "" : scene);
        board.setCaption(caption);
        board.setReview(review.body());
        board.setSubmittedAt(Instant.now());
        boardRepository.save(board);

        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedAt(board.getSubmittedAt());
        session.setOverallRating((double) review.rating());
        DesignSession saved = sessionRepository.save(session);
        practiceLedger.recordDesign(saved);
        return saved;
    }
}
