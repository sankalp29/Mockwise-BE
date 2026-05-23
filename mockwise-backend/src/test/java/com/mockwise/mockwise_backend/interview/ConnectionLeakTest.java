package com.mockwise.mockwise_backend.interview;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that concurrent feedback generation does not leak database connections.
 *
 * Setup:
 *  - HikariCP pool size = 5 (from test application.properties)
 *  - 15 concurrent interviews, each with 2 submissions (30 total Claude calls)
 *  - ClaudeService is mocked to sleep 1 second per call (no external dependency)
 *
 * Pass criteria:
 *  1. All 30 submissions receive feedback (proves pool was not starved).
 *  2. Active connections return to 0 after completion (proves no leak).
 */
@SpringBootTest
class ConnectionLeakTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserSubmissionRepository userSubmissionRepository;

    @Autowired
    private DashboardAggregateRepository dashboardAggregateRepository;

    @MockBean
    private ClaudeService claudeService;

    @Autowired
    private DataSource dataSource;

    private static final int CONCURRENT_REQUESTS = 15;
    private static final int SUBMISSIONS_PER_INTERVIEW = 2;

    @BeforeEach
    void setUp() {
        dashboardAggregateRepository.deleteAll();
        userSubmissionRepository.deleteAllInBatch();
        interviewRepository.deleteAll();
        questionRepository.deleteAllInBatch();
    }

    @Test
    void noConnectionLeakDuringConcurrentFeedbackGeneration() throws Exception {
        // Mock Claude to simulate a slow API call (~1s) and return valid feedback JSON
        String fakeFeedback = "{\"correctness\":{\"score\":7,\"feedback\":\"Good\"},"
                + "\"optimality\":{\"score\":6,\"feedback\":\"OK\"},"
                + "\"timeComplexity\":{\"score\":8,\"feedback\":\"Correct\",\"bigO\":\"O(n)\"},"
                + "\"spaceComplexity\":{\"score\":7,\"feedback\":\"OK\",\"bigO\":\"O(1)\"},"
                + "\"clarity\":{\"score\":8,\"feedback\":\"Readable\"},"
                + "\"overallRating\":7,"
                + "\"overallFeedback\":\"Good solution\","
                + "\"strengths\":[\"Correct\"],\"improvements\":[\"Edge cases\"]}";

        when(claudeService.callClaude(any())).thenAnswer(invocation -> {
            Thread.sleep(1000); // Simulate slow API without hitting real endpoint
            return fakeFeedback;
        });
        when(claudeService.buildCodeFeedbackPrompt(any(), any(), any(), any(), any()))
                .thenReturn("test prompt");

        // Create test data: 2 shared questions, 15 interviews each with 2 submissions
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < SUBMISSIONS_PER_INTERVIEW; i++) {
            Question q = new Question();
            q.setTitle("Test Question " + i);
            q.setDescription("Description " + i);
            q.setDifficulty(Question.Difficulty.EASY);
            questions.add(questionRepository.saveAndFlush(q));
        }

        List<UUID> interviewIds = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            Interview interview = new Interview();
            interview.setUserId("test-user-" + i);
            interview.setUserEmail("test" + i + "@example.com");
            interview.setDifficulty(Question.Difficulty.EASY);
            interview.setNumQuestions(SUBMISSIONS_PER_INTERVIEW);
            interview.setTimeMinutes(30);
            interview.setStartedAt(Instant.now().minusSeconds(1800));
            interview.setEndedAt(Instant.now());
            interview.setStatus(Interview.Status.COMPLETED);
            interview = interviewRepository.saveAndFlush(interview);

            for (Question question : questions) {
                UserSubmission submission = new UserSubmission();
                submission.setInterview(interview);
                submission.setQuestion(question);
                submission.setCode("public int solution() { return 42; }");
                submission.setLanguage("java");
                submission.setSubmittedAt(Instant.now());
                userSubmissionRepository.save(submission);
            }
            interviewIds.add(interview.getId());
        }
        userSubmissionRepository.flush();

        HikariDataSource hikariDs = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMxBean = hikariDs.getHikariPoolMXBean();

        // Fire all 15 feedback generations concurrently (each runs via @Async)
        for (UUID id : interviewIds) {
            interviewService.generateFeedbackForInterview(id);
        }

        // Poll until every submission has feedback or timeout expires
        int totalSubmissions = CONCURRENT_REQUESTS * SUBMISSIONS_PER_INTERVIEW;
        long deadline = System.currentTimeMillis() + 120_000;
        long completedCount = 0;
        while (System.currentTimeMillis() < deadline) {
            completedCount = userSubmissionRepository.findAll().stream()
                    .filter(s -> s.getClaudeFeedback() != null)
                    .count();
            if (completedCount >= totalSubmissions) break;
            Thread.sleep(500);
        }

        // 1. All submissions must have received feedback
        assertEquals(totalSubmissions, completedCount,
                "Expected " + totalSubmissions + " submissions with feedback but only "
                + completedCount + " completed. With pool size " + hikariDs.getMaximumPoolSize()
                + ", a connection leak would starve waiting tasks.");

        // Allow pool to settle
        Thread.sleep(2000);

        // 2. No connections still checked out
        int activeConnections = poolMxBean.getActiveConnections();
        assertEquals(0, activeConnections,
                "Active connections should be 0 after all tasks complete but was " + activeConnections);
    }
}