package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_question_seen", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "question_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestionSeen {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "question_id", nullable = false)
    private UUID questionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private Question.Difficulty difficulty;
    
    @CreationTimestamp
    @Column(name = "seen_at", nullable = false, updatable = false)
    private LocalDateTime seenAt;
    
    // Constructor for easy creation
    public UserQuestionSeen(String userId, UUID questionId, Question.Difficulty difficulty) {
        this.userId = userId;
        this.questionId = questionId;
        this.difficulty = difficulty;
    }
}
