package com.mockwise.backend.dashboard.api;

import com.mockwise.backend.auth.SupabaseUser;
import com.mockwise.backend.common.util.AuthSupport;
import com.mockwise.backend.dashboard.application.DashboardService;
import com.mockwise.backend.dashboard.domain.DashboardAggregate;
import com.mockwise.backend.dashboard.infrastructure.DashboardAggregateRepository;
import com.mockwise.backend.interview.domain.Interview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardAggregateRepository dashboardAggregateRepository;

    @GetMapping("/metrics")
    public ResponseEntity<?> getDashboardData(Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        String userId = user.getId();
        DashboardAggregate agg = dashboardAggregateRepository.findByUserId(userId).orElse(null);

        int totalMocks = agg != null ? agg.getTotalMocks() : 0;
        long totalSeconds = agg != null ? agg.getTotalTimeSpentSeconds() : 0L;
        double avgScore = (agg != null && agg.getRatingCount() > 0)
                ? Math.round((agg.getSumOverallRating() / agg.getRatingCount()) * 10.0) / 10.0
                : 0.0;
        double highScore = agg != null ? agg.getHighestScore() : 0.0;
        double lowScore = (agg != null && agg.getRatingCount() > 0) ? agg.getLowestScore() : 0.0;
        long totalQuestions = agg != null ? agg.getTotalQuestions() : 0L;
        long avgTimePerQuestionSec = (totalQuestions == 0) ? 0 : (totalSeconds / totalQuestions);

        Map<String, Double> scoreByDifficulty = new HashMap<>();
        if (agg != null) {
            double e = (agg.getCntEasy() == 0) ? 0.0 : Math.round((agg.getSumEasy() / agg.getCntEasy()) * 10.0) / 10.0;
            double m = (agg.getCntMedium() == 0) ? 0.0 : Math.round((agg.getSumMedium() / agg.getCntMedium()) * 10.0) / 10.0;
            double h = (agg.getCntHard() == 0) ? 0.0 : Math.round((agg.getSumHard() / agg.getCntHard()) * 10.0) / 10.0;
            scoreByDifficulty.put("Easy", e);
            scoreByDifficulty.put("Medium", m);
            scoreByDifficulty.put("Hard", h);
        }

        String lastMock = (agg != null && agg.getLastMockDate() != null)
                ? DateTimeFormatter.ofPattern("MMM d, yyyy")
                .withZone(ZoneId.systemDefault())
                .format(agg.getLastMockDate())
                : "";

        return ResponseEntity.ok(Map.of(
                "totalInterviews", totalMocks,
                "totalTimeSpentSeconds", totalSeconds,
                "averageScore", avgScore,
                "highestScore", highScore,
                "lowestScore", lowScore,
                "averageTimePerQuestionSeconds", avgTimePerQuestionSec,
                "averageScoreByDifficulty", scoreByDifficulty,
                "lastMockDate", lastMock
        ));
    }

    @GetMapping("/progress")
    public ResponseEntity<?> getProgress(Authentication authentication) {
        SupabaseUser user = AuthSupport.requireUser(authentication);
        String userId = user.getId();
        List<Interview> interviews = dashboardService.getInterviewsForUser(userId);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault());

        List<Map<String, Object>> series = interviews.stream()
                .map(iv -> {
                    double overall = iv.getOverallRating() != null ? iv.getOverallRating() : 0.0;
                    long durationMin = 0L;
                    if (iv.getEndedAt() != null && iv.getStartedAt() != null) {
                        durationMin = Math.max(0,
                                (iv.getEndedAt().getEpochSecond() - iv.getStartedAt().getEpochSecond()) / 60);
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", iv.getId());
                    m.put("date", iv.getStartedAt() != null ? fmt.format(iv.getStartedAt()) : "");
                    m.put("overallRating", overall);
                    m.put("numQuestions", iv.getNumQuestions());
                    m.put("difficulty", iv.getDifficulty().name().substring(0, 1)
                            + iv.getDifficulty().name().substring(1).toLowerCase());
                    m.put("timeMinutes", iv.getTimeMinutes() != null ? iv.getTimeMinutes() : (int) durationMin);
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(series);
    }
}
