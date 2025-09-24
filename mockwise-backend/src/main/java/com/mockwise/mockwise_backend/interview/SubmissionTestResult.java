package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submission_test_results")
@Data
@NoArgsConstructor
public class SubmissionTestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    private UserSubmission submission;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id")
    private QuestionTestCase testCase;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Lob
    @Column(name = "actual_json")
    private String actualJson;

    @Lob
    @Column(name = "expected_json")
    private String expectedJson;

    @Column(name = "time_ms")
    private Long timeMs;

    @Column(name = "memory_kb")
    private Long memoryKb;

    @Lob
    @Column(name = "stderr")
    private String stderr;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}


