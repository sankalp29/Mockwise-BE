package com.mockwise.mockwise_backend.interview;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    
    @Column(name = "judge0_result", columnDefinition = "TEXT")
    private String judge0Result; // Stores summary status (e.g., "Accepted", "Wrong Answer", "Runtime Error")

    @Column(name = "test_case_results_json", columnDefinition = "TEXT")
    private String testCaseResultsJson; // Stores detailed results for multiple test cases in JSON format

    @Column(name = "stdout", columnDefinition = "TEXT")
    private String stdout;

    @Column(name = "stderr", columnDefinition = "TEXT")
    private String stderr;

    @Column(name = "compile_output", columnDefinition = "TEXT")
    private String compileOutput;

    private String time;

    private String memory;
    
    @Column(name = "claude_feedback", columnDefinition = "TEXT")
    private String claudeFeedback; // JSON string from Claude
    
    @Column(name = "feedback_generated_at")
    private Instant feedbackGeneratedAt;
}
