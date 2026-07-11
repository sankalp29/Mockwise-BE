package com.mockwise.backend.dashboard.application;

import com.mockwise.backend.dashboard.domain.DashboardAggregate;
import com.mockwise.backend.dashboard.infrastructure.DashboardAggregateRepository;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final InterviewRepository interviewRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final DashboardAggregateRepository dashboardAggregateRepository;

    public List<Interview> getInterviewsForUser(String userId) {
        return interviewRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, Interview.Status.COMPLETED);
    }

    public List<UserSubmission> getSubmissionsForUser(String userId) {
        long startTime = System.currentTimeMillis();
        log.info("getSubmissionsForUser started for user: {}", userId);

        List<Interview> interviews = getInterviewsForUser(userId);
        List<UUID> interviewIds = interviews.stream().map(Interview::getId).toList();
        log.info("Found {} interviews for user: {}", interviews.size(), userId);

        if (interviewIds.isEmpty()) {
            log.info("getSubmissionsForUser completed in {}ms (no interviews)",
                    System.currentTimeMillis() - startTime);
            return new ArrayList<>();
        }

        List<UserSubmission> submissions = userSubmissionRepository.findByInterviewIdIn(interviewIds);
        log.info("Fetched {} submissions in {}ms", submissions.size(), System.currentTimeMillis() - startTime);
        return submissions;
    }

    @Transactional
    public void updateDashboardAggregate(Interview interview) {
        String userId = interview.getUserId();
        DashboardAggregate agg = dashboardAggregateRepository.findByUserId(userId).orElseGet(() -> {
            DashboardAggregate a = new DashboardAggregate();
            a.setUserId(userId);
            a.setLowestScore(10.0);
            return a;
        });

        long seconds = 0L;
        if (interview.getEndedAt() != null && interview.getStartedAt() != null) {
            seconds = Math.max(0, interview.getEndedAt().getEpochSecond() - interview.getStartedAt().getEpochSecond());
        }

        List<UserSubmission> subs = userSubmissionRepository.findByInterviewId(interview.getId());
        double[] ratings = subs.stream()
                .map(UserSubmission::getClaudeFeedback)
                .filter(f -> f != null && !f.isBlank())
                .map(RatingExtractor::extractOverallRating)
                .filter(r -> r != null && r >= 0)
                .mapToDouble(Double::doubleValue)
                .toArray();
        double overall = ratings.length == 0 ? 0.0 : java.util.Arrays.stream(ratings).average().orElse(0.0);

        double roundedOverall = Math.round(overall * 10.0) / 10.0;
        interview.setOverallRating(roundedOverall);
        interviewRepository.save(interview);

        agg.setTotalMocks(agg.getTotalMocks() + 1);
        agg.setTotalTimeSpentSeconds(agg.getTotalTimeSpentSeconds() + seconds);
        agg.setSumOverallRating(agg.getSumOverallRating() + overall);
        agg.setRatingCount(agg.getRatingCount() + 1);
        agg.setHighestScore(Math.max(agg.getHighestScore(), overall));
        agg.setLowestScore(Math.min(agg.getLowestScore(), overall));
        agg.setTotalQuestions(agg.getTotalQuestions() + interview.getNumQuestions());
        if (agg.getLastMockDate() == null || interview.getStartedAt().isAfter(agg.getLastMockDate())) {
            agg.setLastMockDate(interview.getStartedAt());
        }

        switch (interview.getDifficulty()) {
            case EASY -> { agg.setSumEasy(agg.getSumEasy() + overall); agg.setCntEasy(agg.getCntEasy() + 1); }
            case MEDIUM -> { agg.setSumMedium(agg.getSumMedium() + overall); agg.setCntMedium(agg.getCntMedium() + 1); }
            case HARD -> { agg.setSumHard(agg.getSumHard() + overall); agg.setCntHard(agg.getCntHard() + 1); }
        }

        agg.setUpdatedAt(Instant.now());
        dashboardAggregateRepository.save(agg);
    }
}
