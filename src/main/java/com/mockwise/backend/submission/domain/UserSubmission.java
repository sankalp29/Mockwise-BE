package com.mockwise.backend.submission.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.question.domain.Question;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @ToString.Exclude
    private Question question;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String code;

    @Column(nullable = false)
    private String language = "javascript";

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "claude_feedback", columnDefinition = "TEXT")
    private String claudeFeedback;

    @Column(name = "feedback_generated_at")
    private Instant feedbackGeneratedAt;

    @Column(name = "user_time_complexity", columnDefinition = "TEXT")
    private String userTimeComplexity;

    @Column(name = "user_space_complexity", columnDefinition = "TEXT")
    private String userSpaceComplexity;
}
