package com.mockwise.backend.service.dashboard;

import com.mockwise.backend.repository.dashboard.DashboardAggregate;
import com.mockwise.backend.repository.dashboard.DashboardAggregateRepository;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.submission.UserSubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceMetricsTest {

    @Mock private InterviewRepository interviewRepository;
    @Mock private UserSubmissionRepository userSubmissionRepository;
    @Mock private DashboardAggregateRepository dashboardAggregateRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void metricsAreZeroWhenTheUserHasNoAggregate() {
        when(dashboardAggregateRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        Map<String, Object> metrics = dashboardService.metricsFor("user-1");

        assertEquals(0, metrics.get("totalInterviews"));
        assertEquals(0L, metrics.get("totalTimeSpentSeconds"));
        assertEquals(0.0, metrics.get("averageScore"));
        assertEquals(0.0, metrics.get("highestScore"));
        assertEquals(0.0, metrics.get("lowestScore"));
        assertEquals(0L, metrics.get("averageTimePerQuestionSeconds"));
        assertEquals("", metrics.get("lastMockDate"));
        assertEquals(Map.of(), metrics.get("averageScoreByDifficulty"));
    }

    @Test
    void metricsRoundAveragesFromTheStoredAggregate() {
        DashboardAggregate aggregate = new DashboardAggregate();
        aggregate.setTotalMocks(2);
        aggregate.setTotalTimeSpentSeconds(120);
        aggregate.setTotalQuestions(4);
        aggregate.setSumOverallRating(15.0);
        aggregate.setRatingCount(2);
        aggregate.setHighestScore(9.0);
        aggregate.setLowestScore(6.0);
        aggregate.setSumEasy(9.0);
        aggregate.setCntEasy(1);
        aggregate.setSumMedium(0);
        aggregate.setCntMedium(0);
        aggregate.setSumHard(12.0);
        aggregate.setCntHard(2);
        aggregate.setLastMockDate(Instant.parse("2026-03-01T00:00:00Z"));
        when(dashboardAggregateRepository.findByUserId("user-1")).thenReturn(Optional.of(aggregate));

        Map<String, Object> metrics = dashboardService.metricsFor("user-1");

        assertEquals(2, metrics.get("totalInterviews"));
        assertEquals(7.5, metrics.get("averageScore"));
        assertEquals(30L, metrics.get("averageTimePerQuestionSeconds"));
        @SuppressWarnings("unchecked")
        Map<String, Double> byDifficulty = (Map<String, Double>) metrics.get("averageScoreByDifficulty");
        assertEquals(9.0, byDifficulty.get("Easy"));
        assertEquals(0.0, byDifficulty.get("Medium"));
        assertEquals(6.0, byDifficulty.get("Hard"));
    }
}
