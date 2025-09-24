package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_code_stubs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"question_id", "language"}))
@Data
@NoArgsConstructor
public class QuestionCodeStub {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "language", nullable = false, length = 32)
    private String language; // e.g., "java", "python", "cpp"

    @Column(name = "stub", nullable = false, columnDefinition = "text")
    private String stub;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}


