package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dashboard_aggregate")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAggregate {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "total_mocks")
    private int totalMocks;

    @Column(name = "total_time_sec")
    private long totalTimeSpentSeconds;

    @Column(name = "sum_overall")
    private double sumOverallRating;

    @Column(name = "rating_count")
    private int ratingCount;

    @Column(name = "highest_score")
    private double highestScore;

    @Column(name = "lowest_score")
    private double lowestScore;

    @Column(name = "sum_easy")
    private double sumEasy;
    @Column(name = "cnt_easy")
    private int cntEasy;

    @Column(name = "sum_medium")
    private double sumMedium;
    @Column(name = "cnt_medium")
    private int cntMedium;

    @Column(name = "sum_hard")
    private double sumHard;
    @Column(name = "cnt_hard")
    private int cntHard;

    @Column(name = "total_questions")
    private long totalQuestions;

    @Column(name = "last_mock_date")
    private Instant lastMockDate;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // AI Insights (cached)
    @Column(name = "insights_json", columnDefinition = "TEXT")
    private String insightsJson;

    @Column(name = "insights_interview_count")
    private Integer insightsInterviewCount; // number of totalMocks when insights were last generated
}


