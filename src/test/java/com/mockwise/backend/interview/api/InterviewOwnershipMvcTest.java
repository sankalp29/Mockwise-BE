package com.mockwise.backend.interview.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.backend.interview.application.FeedbackGenerationWorker;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.domain.InterviewQuestion;
import com.mockwise.backend.interview.infrastructure.InterviewQuestionRepository;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.question.infrastructure.QuestionRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level ownership / lifecycle gates for PR-01.
 * Proves generate-feedback returns 403 on the request thread (not async 200).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewOwnershipMvcTest {

    private static final String OWNER = "owner-user-001";
    private static final String OTHER = "other-user-002";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewQuestionRepository interviewQuestionRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private UserSubmissionRepository userSubmissionRepository;

    @MockBean
    private FeedbackGenerationWorker feedbackGenerationWorker;

    private Question question;
    private Interview ownedInterview;

    @BeforeEach
    void setUp() {
        // FK order: submissions → interview_questions → interviews → questions
        userSubmissionRepository.deleteAllInBatch();
        interviewQuestionRepository.deleteAllInBatch();
        interviewRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch();

        question = new Question();
        question.setTitle("MVC Q");
        question.setDescription("desc");
        question.setDifficulty(Question.Difficulty.EASY);
        question = questionRepository.saveAndFlush(question);

        ownedInterview = new Interview();
        ownedInterview.setUserId(OWNER);
        ownedInterview.setUserEmail("owner@test.com");
        ownedInterview.setDifficulty(Question.Difficulty.EASY);
        ownedInterview.setNumQuestions(1);
        ownedInterview.setTimeMinutes(45);
        ownedInterview.setStartedAt(Instant.now().minusSeconds(120));
        ownedInterview.setStatus(Interview.Status.IN_PROGRESS);
        ownedInterview.setAssignedQuestionLinks(
                java.util.List.of(new InterviewQuestion(ownedInterview, question, 1)));
        ownedInterview = interviewRepository.saveAndFlush(ownedInterview);
    }

    @Test
    void generateFeedback_nonOwner_returns403_andDoesNotScheduleWorker() throws Exception {
        ownedInterview.setStatus(Interview.Status.COMPLETED);
        ownedInterview.setEndedAt(Instant.now());
        interviewRepository.saveAndFlush(ownedInterview);
        saveSubmission(null);

        mockMvc.perform(post("/api/interview/{id}/generate-feedback", ownedInterview.getId())
                        .header("Authorization", bearer(OTHER))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void generateFeedback_ownerZeroSubmissions_returns400() throws Exception {
        ownedInterview.setStatus(Interview.Status.COMPLETED);
        ownedInterview.setEndedAt(Instant.now());
        interviewRepository.saveAndFlush(ownedInterview);

        mockMvc.perform(post("/api/interview/{id}/generate-feedback", ownedInterview.getId())
                        .header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void generateFeedback_ownerReady_returnsAlreadyReady_withoutWorker() throws Exception {
        ownedInterview.setStatus(Interview.Status.COMPLETED);
        ownedInterview.setEndedAt(Instant.now());
        interviewRepository.saveAndFlush(ownedInterview);
        saveSubmission("{\"overallRating\":9}");

        mockMvc.perform(post("/api/interview/{id}/generate-feedback", ownedInterview.getId())
                        .header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"))
                .andExpect(jsonPath("$.statusCode").value("ALREADY_READY"))
                .andExpect(jsonPath("$.interviewId").value(ownedInterview.getId().toString()));

        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void generateFeedback_ownerPending_returnsStarted_andSchedulesWorker() throws Exception {
        ownedInterview.setStatus(Interview.Status.COMPLETED);
        ownedInterview.setEndedAt(Instant.now());
        interviewRepository.saveAndFlush(ownedInterview);
        saveSubmission(null);

        mockMvc.perform(post("/api/interview/{id}/generate-feedback", ownedInterview.getId())
                        .header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("started"))
                .andExpect(jsonPath("$.statusCode").value("STARTED"))
                .andExpect(jsonPath("$.interviewId").value(ownedInterview.getId().toString()));

        verify(feedbackGenerationWorker, times(1)).runFeedbackGeneration(ownedInterview.getId());
    }

    @Test
    void submit_nonOwner_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "submissions", java.util.List.of(Map.of(
                        "questionId", question.getId().toString(),
                        "code", "int x=1;",
                        "language", "java"
                ))
        );

        mockMvc.perform(post("/api/interview/{id}/submit", ownedInterview.getId())
                        .header("Authorization", bearer(OTHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void submit_alreadyCompleted_returns409() throws Exception {
        ownedInterview.setStatus(Interview.Status.COMPLETED);
        ownedInterview.setEndedAt(Instant.now());
        interviewRepository.saveAndFlush(ownedInterview);

        Map<String, Object> body = Map.of(
                "submissions", java.util.List.of(Map.of(
                        "questionId", question.getId().toString(),
                        "code", "int x=1;",
                        "language", "java"
                ))
        );

        mockMvc.perform(post("/api/interview/{id}/submit", ownedInterview.getId())
                        .header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verify(feedbackGenerationWorker, never()).runFeedbackGeneration(any());
    }

    @Test
    void submit_ownerInProgress_returns200_andSchedulesWorker() throws Exception {
        Map<String, Object> body = Map.of(
                "submissions", java.util.List.of(Map.of(
                        "questionId", question.getId().toString(),
                        "code", "int x=1;",
                        "language", "java",
                        "timeComplexity", "O(n)",
                        "spaceComplexity", "O(1)"
                ))
        );

        mockMvc.perform(post("/api/interview/{id}/submit", ownedInterview.getId())
                        .header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interviewId").value(ownedInterview.getId().toString()));

        verify(feedbackGenerationWorker, times(1)).runFeedbackGeneration(ownedInterview.getId());
    }

    private void saveSubmission(String feedback) {
        UserSubmission s = new UserSubmission();
        s.setInterview(ownedInterview);
        s.setQuestion(question);
        s.setCode("code");
        s.setLanguage("java");
        s.setSubmittedAt(Instant.now());
        s.setClaudeFeedback(feedback);
        if (feedback != null) {
            s.setFeedbackGeneratedAt(Instant.now());
        }
        userSubmissionRepository.saveAndFlush(s);
    }

    private static String bearer(String userId) {
        return "Bearer " + unsignedJwt(userId, userId + "@test.com");
    }

    private static String unsignedJwt(String sub, String email) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(
                ("{\"sub\":\"" + sub + "\",\"email\":\"" + email + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }
}
