package com.mockwise.backend.service.session;

import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * One user may have a single live interview, whether it is coding or system design.
 * A session counts as live only while it is in progress and its allotted time has not elapsed.
 */
@Service
@RequiredArgsConstructor
public class ActiveSessionGuard {

    public static final String ALREADY_IN_PROGRESS = "You already have an interview in progress.";

    private final InterviewRepository interviewRepository;
    private final DesignSessionRepository designSessionRepository;

    @Transactional(readOnly = true)
    public void requireClear(String userId) {
        if (hasLiveCoding(userId) || hasLiveDesign(userId)) {
            throw new ConflictException(ALREADY_IN_PROGRESS);
        }
    }

    private boolean hasLiveCoding(String userId) {
        for (Interview interview : interviewRepository.findByUserIdAndStatus(userId, Interview.Status.IN_PROGRESS)) {
            if (withinTime(interview.getStartedAt(), interview.getTimeMinutes())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLiveDesign(String userId) {
        for (DesignSession session : designSessionRepository.findByUserIdAndStatus(userId, SessionStatus.IN_PROGRESS)) {
            if (withinTime(session.getStartedAt(), session.getTimeMinutes())) {
                return true;
            }
        }
        return false;
    }

    private boolean withinTime(Instant startedAt, Integer timeMinutes) {
        if (startedAt == null || timeMinutes == null || timeMinutes <= 0) {
            return false;
        }
        return Duration.between(startedAt, Instant.now()).toMinutes() < timeMinutes;
    }
}
