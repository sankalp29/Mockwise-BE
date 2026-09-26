package com.mockwise.backend.service.dashboard;

import com.mockwise.backend.repository.dashboard.Practice;
import com.mockwise.backend.repository.dashboard.PracticeResult;
import com.mockwise.backend.repository.dashboard.PracticeResultRepository;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.systemdesign.DesignSession;
import com.mockwise.backend.repository.systemdesign.DesignSessionRepository;
import com.mockwise.backend.repository.systemdesign.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PracticeLedger {

    private final PracticeResultRepository practiceResultRepository;
    private final InterviewRepository interviewRepository;
    private final DesignSessionRepository designSessionRepository;

    @Transactional
    public void sync(String userId) {
        for (Interview interview : interviewRepository.findByUserIdAndStatus(
                userId, Interview.Status.COMPLETED)) {
            recordCoding(interview);
        }
        for (DesignSession session : designSessionRepository.findByUserIdAndStatus(
                userId, SessionStatus.COMPLETED)) {
            recordDesign(session);
        }
    }

    @Transactional
    public void recordCoding(Interview interview) {
        if (interview.getId() == null || interview.getStatus() != Interview.Status.COMPLETED) {
            return;
        }
        if (practiceResultRepository.findByPracticeAndSourceKey(Practice.CODING, interview.getId()).isPresent()) {
            return;
        }
        PracticeResult row = new PracticeResult();
        row.setUserId(interview.getUserId());
        row.setPractice(Practice.CODING);
        row.setSourceKey(interview.getId());
        row.setDifficulty(interview.getDifficulty());
        row.setQuestionCount(interview.getNumQuestions() == null ? 0 : interview.getNumQuestions());
        row.setOverallRating(interview.getOverallRating() == null ? 0.0 : interview.getOverallRating());
        row.setTimeMinutes(minutes(interview.getStartedAt(), interview.getEndedAt(), interview.getTimeMinutes()));
        row.setStartedAt(interview.getStartedAt() == null ? Instant.now() : interview.getStartedAt());
        practiceResultRepository.save(row);
    }

    @Transactional
    public void recordDesign(DesignSession session) {
        if (session.getId() == null || session.getStatus() != SessionStatus.COMPLETED) {
            return;
        }
        if (practiceResultRepository.findByPracticeAndSourceKey(Practice.SYSTEM_DESIGN, session.getId()).isPresent()) {
            return;
        }
        PracticeResult row = new PracticeResult();
        row.setUserId(session.getUserId());
        row.setPractice(Practice.SYSTEM_DESIGN);
        row.setSourceKey(session.getId());
        row.setDifficulty(session.getLevel());
        row.setQuestionCount(1);
        row.setOverallRating(session.getOverallRating() == null ? 0.0 : session.getOverallRating());
        row.setTimeMinutes(minutes(session.getStartedAt(), session.getEndedAt(), session.getTimeMinutes()));
        row.setStartedAt(session.getStartedAt() == null ? Instant.now() : session.getStartedAt());
        practiceResultRepository.save(row);
    }

    public List<PracticeResult> completed(String userId, Practice practice) {
        if (practice == null) {
            return practiceResultRepository.findByUserIdOrderByStartedAtDesc(userId);
        }
        return practiceResultRepository.findByUserIdAndPracticeOrderByStartedAtDesc(userId, practice);
    }

    private static int minutes(Instant started, Instant ended, Integer allotted) {
        if (started != null && ended != null) {
            long elapsed = Math.max(0, ended.getEpochSecond() - started.getEpochSecond()) / 60;
            if (elapsed > 0) {
                return (int) elapsed;
            }
        }
        return allotted == null ? 0 : allotted;
    }
}
