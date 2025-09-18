package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "optimal_solutions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_optimal_solution_question_language", columnNames = {"question_id", "language"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptimalSolution {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "code", columnDefinition = "TEXT", nullable = false)
    private String code;
}


