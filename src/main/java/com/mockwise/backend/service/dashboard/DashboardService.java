package com.mockwise.backend.service.dashboard;

import com.mockwise.backend.repository.dashboard.DashboardAggregate;
import com.mockwise.backend.repository.dashboard.DashboardAggregateRepository;
import com.mockwise.backend.repository.dashboard.Practice;
import com.mockwise.backend.repository.dashboard.PracticeResult;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.repository.submission.UserSubmission;
import com.mockwise.backend.repository.submission.UserSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final InterviewRepository interviewRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final DashboardAggregateRepository dashboardAggregateRepository;
    private final PracticeLedger practiceLedger;

    public List<Interview> getInterviewsForUser(String userId) {
        return interviewRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, Interview.Status.COMPLETED);
    }

    @Transactional
    public Map<String, Object> metricsFor(String userId, Practice practice) {
        practiceLedger.sync(userId);
        return summarize(practiceLedger.completed(userId, practice));
    }

    public Map<String, Object> metricsFor(String userId) {
        DashboardAggregate aggregate = dashboardAggregateRepository.findByUserId(userId).orElse(null);

        int totalMocks = aggregate != null ? aggregate.getTotalMocks() : 0;
        long totalSeconds = aggregate != null ? aggregate.getTotalTimeSpentSeconds() : 0L;
        double averageScore = aggregate != null
                ? roundedAverage(aggregate.getSumOverallRating(), aggregate.getRatingCount())
                : 0.0;
        double highestScore = aggregate != null ? aggregate.getHighestScore() : 0.0;
        double lowestScore = (aggregate != null && aggregate.getRatingCount() > 0)
                ? aggregate.getLowestScore()
                : 0.0;
        long totalQuestions = aggregate != null ? aggregate.getTotalQuestions() : 0L;
        long averageTimePerQuestionSeconds = totalQuestions == 0 ? 0 : (totalSeconds / totalQuestions);

        Map<String, Double> scoreByDifficulty = new LinkedHashMap<>();
        if (aggregate != null) {
            scoreByDifficulty.put("Easy", roundedAverage(aggregate.getSumEasy(), aggregate.getCntEasy()));
            scoreByDifficulty.put("Medium", roundedAverage(aggregate.getSumMedium(), aggregate.getCntMedium()));
            scoreByDifficulty.put("Hard", roundedAverage(aggregate.getSumHard(), aggregate.getCntHard()));
        }

        String lastMockDate = "";
        if (aggregate != null && aggregate.getLastMockDate() != null) {
            lastMockDate = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.systemDefault())
                    .format(aggregate.getLastMockDate());
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalInterviews", totalMocks);
        metrics.put("totalTimeSpentSeconds", totalSeconds);
        metrics.put("averageScore", averageScore);
        metrics.put("highestScore", highestScore);
        metrics.put("lowestScore", lowestScore);
        metrics.put("averageTimePerQuestionSeconds", averageTimePerQuestionSeconds);
        metrics.put("averageScoreByDifficulty", scoreByDifficulty);
        metrics.put("lastMockDate", lastMockDate);
        return metrics;
    }

    @Transactional
    public List<Map<String, Object>> historyFor(String userId, Practice practice) {
        practiceLedger.sync(userId);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault());
        List<Map<String, Object>> series = new ArrayList<>();
        for (PracticeResult result : practiceLedger.completed(userId, practice)) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", result.getSourceKey());
            point.put("practice", result.getPractice().name());
            point.put("practiceLabel", result.getPractice().label());
            point.put("date", result.getStartedAt() != null ? dateFormat.format(result.getStartedAt()) : "");
            point.put("overallRating", result.getOverallRating());
            point.put("numQuestions", result.getQuestionCount());
            point.put("difficulty", titleCase(result.getDifficulty()));
            point.put("timeMinutes", result.getTimeMinutes());
            series.add(point);
        }
        return series;
    }

    public List<Map<String, Object>> progressFor(String userId) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault());
        List<Map<String, Object>> series = new ArrayList<>();
        for (Interview interview : getInterviewsForUser(userId)) {
            double overallRating = interview.getOverallRating() != null ? interview.getOverallRating() : 0.0;
            long durationMinutes = 0L;
            if (interview.getEndedAt() != null && interview.getStartedAt() != null) {
                durationMinutes = Math.max(0,
                        (interview.getEndedAt().getEpochSecond() - interview.getStartedAt().getEpochSecond()) / 60);
            }
            Map<String, Object> point = new HashMap<>();
            point.put("id", interview.getId());
            point.put("date", interview.getStartedAt() != null ? dateFormat.format(interview.getStartedAt()) : "");
            point.put("overallRating", overallRating);
            point.put("numQuestions", interview.getNumQuestions());
            point.put("difficulty", interview.getDifficulty().name().substring(0, 1)
                    + interview.getDifficulty().name().substring(1).toLowerCase());
            point.put("timeMinutes", interview.getTimeMinutes() != null
                    ? interview.getTimeMinutes()
                    : (int) durationMinutes);
            series.add(point);
        }
        return series;
    }

    private static double roundedAverage(double sum, int count) {
        if (count == 0) {
            return 0.0;
        }
        return Math.round((sum / count) * 10.0) / 10.0;
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
        DashboardAggregate aggregate = dashboardAggregateRepository.findByUserId(userId).orElseGet(() -> {
            DashboardAggregate created = new DashboardAggregate();
            created.setUserId(userId);
            created.setLowestScore(10.0);
            return created;
        });

        long seconds = 0L;
        if (interview.getEndedAt() != null && interview.getStartedAt() != null) {
            seconds = Math.max(0, interview.getEndedAt().getEpochSecond() - interview.getStartedAt().getEpochSecond());
        }

        List<UserSubmission> submissions = userSubmissionRepository.findByInterviewId(interview.getId());
        double[] ratings = submissions.stream()
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

        aggregate.setTotalMocks(aggregate.getTotalMocks() + 1);
        aggregate.setTotalTimeSpentSeconds(aggregate.getTotalTimeSpentSeconds() + seconds);
        aggregate.setSumOverallRating(aggregate.getSumOverallRating() + overall);
        aggregate.setRatingCount(aggregate.getRatingCount() + 1);
        aggregate.setHighestScore(Math.max(aggregate.getHighestScore(), overall));
        aggregate.setLowestScore(Math.min(aggregate.getLowestScore(), overall));
        aggregate.setTotalQuestions(aggregate.getTotalQuestions() + interview.getNumQuestions());
        if (aggregate.getLastMockDate() == null || interview.getStartedAt().isAfter(aggregate.getLastMockDate())) {
            aggregate.setLastMockDate(interview.getStartedAt());
        }

        switch (interview.getDifficulty()) {
            case EASY -> {
                aggregate.setSumEasy(aggregate.getSumEasy() + overall);
                aggregate.setCntEasy(aggregate.getCntEasy() + 1);
            }
            case MEDIUM -> {
                aggregate.setSumMedium(aggregate.getSumMedium() + overall);
                aggregate.setCntMedium(aggregate.getCntMedium() + 1);
            }
            case HARD -> {
                aggregate.setSumHard(aggregate.getSumHard() + overall);
                aggregate.setCntHard(aggregate.getCntHard() + 1);
            }
        }

        aggregate.setUpdatedAt(Instant.now());
        dashboardAggregateRepository.save(aggregate);
        practiceLedger.recordCoding(interview);
    }

    private Map<String, Object> summarize(List<PracticeResult> rows) {
        int totalMocks = rows.size();
        long totalSeconds = rows.stream().mapToLong(row -> row.getTimeMinutes() * 60L).sum();
        long totalQuestions = rows.stream().mapToLong(PracticeResult::getQuestionCount).sum();
        double sum = rows.stream().mapToDouble(PracticeResult::getOverallRating).sum();
        double highest = rows.stream().mapToDouble(PracticeResult::getOverallRating).max().orElse(0.0);
        double lowest = rows.stream().mapToDouble(PracticeResult::getOverallRating).min().orElse(0.0);
        Instant last = rows.stream().map(PracticeResult::getStartedAt).filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(null);

        Map<String, Double> scoreByDifficulty = new LinkedHashMap<>();
        scoreByDifficulty.put("Easy", averageFor(rows, "EASY"));
        scoreByDifficulty.put("Medium", averageFor(rows, "MEDIUM"));
        scoreByDifficulty.put("Hard", averageFor(rows, "HARD"));

        String lastMockDate = "";
        if (last != null) {
            lastMockDate = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.systemDefault())
                    .format(last);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalInterviews", totalMocks);
        metrics.put("totalTimeSpentSeconds", totalSeconds);
        metrics.put("averageScore", roundedAverage(sum, totalMocks));
        metrics.put("highestScore", Math.round(highest * 10.0) / 10.0);
        metrics.put("lowestScore", totalMocks == 0 ? 0.0 : Math.round(lowest * 10.0) / 10.0);
        metrics.put("averageTimePerQuestionSeconds", totalQuestions == 0 ? 0 : totalSeconds / totalQuestions);
        metrics.put("averageScoreByDifficulty", scoreByDifficulty);
        metrics.put("lastMockDate", lastMockDate);
        return metrics;
    }

    private static double averageFor(List<PracticeResult> rows, String difficulty) {
        double sum = 0;
        int count = 0;
        for (PracticeResult row : rows) {
            if (difficulty.equalsIgnoreCase(row.getDifficulty())) {
                sum += row.getOverallRating();
                count++;
            }
        }
        return roundedAverage(sum, count);
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
