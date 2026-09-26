package com.mockwise.backend.repository.dashboard;

import com.mockwise.backend.repository.question.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_results", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_result_source", columnNames = {"practice", "source_key"})
})
@Data
@NoArgsConstructor
public class PracticeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Practice practice;

    @Column(name = "source_key", nullable = false)
    private UUID sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "overall_rating", nullable = false)
    private double overallRating;

    @Column(name = "time_minutes", nullable = false)
    private int timeMinutes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
}
