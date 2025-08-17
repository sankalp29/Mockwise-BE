package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "interviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Interview {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private String userId; // Supabase user ID
    
    @Column(name = "user_email", nullable = false)
    private String userEmail;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Question.Difficulty difficulty;
    
    @Column(name = "num_questions", nullable = false)
    private Integer numQuestions;
    
    @Column(name = "time_minutes", nullable = false)
    private Integer timeMinutes;
    
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    
    @Column(name = "ended_at")
    private Instant endedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IN_PROGRESS;
    
    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserSubmission> submissions;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "interview_questions",
        joinColumns = @JoinColumn(name = "interview_id"),
        inverseJoinColumns = @JoinColumn(name = "question_id")
    )
    private List<Question> assignedQuestions;
    
    public enum Status {
        IN_PROGRESS, COMPLETED, ABANDONED
    }
}
