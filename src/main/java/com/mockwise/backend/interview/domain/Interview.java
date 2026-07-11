package com.mockwise.backend.interview.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.submission.domain.UserSubmission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
    private String userId;

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

    @Column(name = "aggregated")
    private Boolean aggregated = false;

    @Column(name = "overall_rating")
    private Double overallRating;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<UserSubmission> submissions;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<InterviewQuestion> assignedQuestionLinks;

    /**
     * Convenience for API responses when links are loaded in-session.
     * Not serialized by default to avoid LazyInitializationException after the session closes.
     * Controllers that need questions should put them in the response map explicitly.
     */
    @Transient
    @JsonIgnore
    public List<Question> getAssignedQuestions() {
        if (assignedQuestionLinks == null) {
            return List.of();
        }
        return assignedQuestionLinks.stream().map(InterviewQuestion::getQuestion).toList();
    }

    public enum Status {
        IN_PROGRESS, COMPLETED, ABANDONED
    }
}
