package com.mockwise.backend.interview.application;

import com.mockwise.backend.evaluation.ClaudeService;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.infrastructure.InterviewQuestionRepository;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.question.infrastructure.QuestionRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies concurrent feedback generation does not leak Hikari connections.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeedbackConnectionLeakTest {

    private static final int CONCURRENT_REQUESTS = 10;
    private static final int SUBMISSIONS_PER_INTERVIEW = 2;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private InterviewQuestionRepository interviewQuestionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserSubmissionRepository userSubmissionRepository;

    @MockBean
    private ClaudeService claudeService;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        userSubmissionRepository.deleteAllInBatch();
        interviewQuestionRepository.deleteAllInBatch();
        interviewRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();
    }

    @Test
    void noConnectionLeakDuringConcurrentFeedbackGeneration() throws Exception {
        String fakeFeedback = "{\"overallRating\": 7, \"overallFeedback\": \"Good\"}";

        when(claudeService.buildCodeFeedbackPrompt(any(), any(), any(), any(), any()))
                .thenReturn("test prompt");
        when(claudeService.callClaude(any())).thenAnswer(invocation -> {
            Thread.sleep(200);
            return fakeFeedback;
        });

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
            interview.setAggregated(false);
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

        HikariDataSource hikariDs = dataSource.unwrap(HikariDataSource.class);
        HikariPoolMXBean poolMxBean = hikariDs.getHikariPoolMXBean();

        for (UUID id : interviewIds) {
            feedbackService.runFeedbackGeneration(id);
        }

        int totalSubmissions = CONCURRENT_REQUESTS * SUBMISSIONS_PER_INTERVIEW;
        long deadline = System.currentTimeMillis() + 60_000;
        long completedCount = 0;
        while (System.currentTimeMillis() < deadline) {
            completedCount = userSubmissionRepository.findAll().stream()
                    .filter(s -> s.getClaudeFeedback() != null)
                    .count();
            if (completedCount >= totalSubmissions) {
                break;
            }
            Thread.sleep(200);
        }

        assertEquals(totalSubmissions, completedCount,
                "Expected all submissions to receive feedback; pool starvation would leave some unset");

        Thread.sleep(1500);

        int activeConnections = poolMxBean.getActiveConnections();
        assertEquals(0, activeConnections,
                "Active connections should be 0 after feedback completes but was " + activeConnections
                        + " (pool max=" + hikariDs.getMaximumPoolSize() + ")");
        assertTrue(poolMxBean.getThreadsAwaitingConnection() == 0);
    }
}
