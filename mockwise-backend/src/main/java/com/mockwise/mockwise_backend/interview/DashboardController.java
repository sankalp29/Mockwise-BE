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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
        } catch (Exception e) {
            log.error("Error building dashboard metrics", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/progress")
    public ResponseEntity<?> getProgress(Authentication authentication) {
        long startTime = System.currentTimeMillis();
        log.info("Dashboard progress API started for user: {}", authentication.getName());
        
        try {
            SupabaseAuthService.SupabaseUser user = extractSupabaseUser(authentication);
            String userId = user.getId();
            List<Interview> interviews = interviewService.getInterviewsForUser(userId);
            log.info("Found {} interviews for user: {}", interviews.size(), userId);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault());

            List<Map<String, Object>> series = interviews.stream()
                    .map(iv -> {
                        // OPTIMIZATION: Use denormalized rating field instead of calculating from submissions
                        double overall = iv.getOverallRating() != null ? iv.getOverallRating() : 0.0;

                        long durationMin = 0L;
                        if (iv.getEndedAt() != null && iv.getStartedAt() != null) {
                            durationMin = Math.max(0, (iv.getEndedAt().getEpochSecond() - iv.getStartedAt().getEpochSecond()) / 60);
                        }

                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", iv.getId());
                        m.put("date", iv.getStartedAt() != null ? fmt.format(iv.getStartedAt()) : "");
                        m.put("overallRating", overall); // Already rounded in the entity
                        m.put("numQuestions", iv.getNumQuestions());
                        m.put("difficulty", iv.getDifficulty().name().substring(0,1) + iv.getDifficulty().name().substring(1).toLowerCase());
                        m.put("timeMinutes", iv.getTimeMinutes() != null ? iv.getTimeMinutes() : (int) durationMin);
                        return m;
                    })
                    .collect(Collectors.toList());

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            log.info("sDashboard progress API completed in {}ms for user: {}", totalTime, userId);

            return ResponseEntity.ok(series);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            log.error("❌ Dashboard progress API failed after {}ms: {}", totalTime, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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


