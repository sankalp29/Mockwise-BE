package com.mockwise.mockwise_backend.interview;

import com.mockwise.mockwise_backend.auth.SupabaseAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final InterviewService interviewService;
    private final DashboardAggregateRepository dashboardAggregateRepository;

    @GetMapping("/metrics")
    public ResponseEntity<?> getDashboardData(Authentication authentication) {
        try {
            SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
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

            java.util.Map<String, Double> scoreByDifficulty = new java.util.HashMap<>();
            if (agg != null) {
                double e = (agg.getCntEasy() == 0) ? 0.0 : Math.round((agg.getSumEasy() / agg.getCntEasy()) * 10.0) / 10.0;
                double m = (agg.getCntMedium() == 0) ? 0.0 : Math.round((agg.getSumMedium() / agg.getCntMedium()) * 10.0) / 10.0;
                double h = (agg.getCntHard() == 0) ? 0.0 : Math.round((agg.getSumHard() / agg.getCntHard()) * 10.0) / 10.0;
                scoreByDifficulty.put("Easy", e);
                scoreByDifficulty.put("Medium", m);
                scoreByDifficulty.put("Hard", h);
            }

            String lastMock = (agg != null && agg.getLastMockDate() != null)
                    ? java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(agg.getLastMockDate())
                    : "";

            String insights = interviewService.getInsightsJson(userId);
            insights = insights != null ? insights : "No insights available";

            return ResponseEntity.ok(Map.of(
                    "totalInterviews", totalMocks,
                    "totalTimeSpentSeconds", totalSeconds,
                    "averageScore", avgScore,
                    "highestScore", highScore,
                    "lowestScore", lowScore,
                    "averageTimePerQuestionSeconds", avgTimePerQuestionSec,
                    "averageScoreByDifficulty", scoreByDifficulty,
                    "lastMockDate", lastMock,
                    "aiInsights", insights
            ));
        } catch (Exception e) {
            log.error("Error building dashboard metrics", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/insights")
    public ResponseEntity<?> getInsights(Authentication authentication) {
        SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
        String userId = user.getId();
        String insights = interviewService.getInsightsJson(userId);
        return ResponseEntity.ok(Map.of("aiInsights", insights));
    }

    private SupabaseAuthService.SupabaseUser extractSupabaseUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No authentication found");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseAuthService.SupabaseUser user) {
            return user;
        }
        throw new IllegalArgumentException("Invalid authentication type: " + principal.getClass().getSimpleName());
    }
}


