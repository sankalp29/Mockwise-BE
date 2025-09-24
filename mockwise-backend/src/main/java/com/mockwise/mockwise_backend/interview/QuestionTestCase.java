package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "question_test_cases")
@Data
@NoArgsConstructor
public class QuestionTestCase {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid default gen_random_uuid()", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "name", nullable = false)
    private String name;

    // Language-agnostic arguments and expected output stored as JSON strings
    @Column(name = "args_json", nullable = false, columnDefinition = "jsonb")
    private String argsJson;

    @Column(name = "expected_json", nullable = false, columnDefinition = "jsonb")
    private String expectedJson;

    @Column(name = "comparator", nullable = false)
    private String comparator = "exact"; // exact | float_tolerance | unordered

    @Column(name = "tolerance")
    private Double tolerance; // for float_tolerance comparator

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "time_limit_ms")
    private Integer timeLimitMs; // optional per case override

    @Column(name = "memory_limit_kb")
    private Integer memoryLimitKb; // optional per case override
}


