package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "interview_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {
    
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    
    @Column(name = "question_order")
    private Integer questionOrder;
    
    @Column(name = "assigned_at")
    private java.time.Instant assignedAt = java.time.Instant.now();
     
    public InterviewQuestion(Interview interview, Question question, Integer questionOrder) {
        this.interview = interview;
        this.question = question;
        this.questionOrder = questionOrder;
        this.assignedAt = java.time.Instant.now();
    }
}
