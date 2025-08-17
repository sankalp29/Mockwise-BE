package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "interview_question")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "interview_id", nullable = false)
    private UUID interviewId;
    
    @Column(name = "question_id", nullable = false)
    private UUID questionId;
    
    @Column(name = "question_order")
    private Integer questionOrder;
    
    @Column(name = "assigned_at")
    private java.time.Instant assignedAt = java.time.Instant.now();
    
    // Constructor for convenience
    public InterviewQuestion(UUID interviewId, UUID questionId, Integer questionOrder) {
        this.interviewId = interviewId;
        this.questionId = questionId;
        this.questionOrder = questionOrder;
        this.assignedAt = java.time.Instant.now();
    }
}
