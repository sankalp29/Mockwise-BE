package com.mockwise.backend.interview.application;

import com.mockwise.backend.auth.SupabaseUser;
import com.mockwise.backend.common.exception.BadRequestException;
import com.mockwise.backend.common.exception.ConflictException;
import com.mockwise.backend.common.exception.ForbiddenException;
import com.mockwise.backend.common.exception.ResourceNotFoundException;
import com.mockwise.backend.interview.api.dto.SubmissionRequest;
import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.interview.domain.InterviewQuestion;
import com.mockwise.backend.interview.infrastructure.InterviewQuestionRepository;
import com.mockwise.backend.interview.infrastructure.InterviewRepository;
import com.mockwise.backend.progress.application.UserQuestionSeenService;
import com.mockwise.backend.question.application.QuestionSelectionService;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.question.infrastructure.QuestionRepository;
import com.mockwise.backend.submission.domain.UserSubmission;
import com.mockwise.backend.submission.infrastructure.UserSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final QuestionSelectionService questionSelectionService;
    private final UserQuestionSeenService userQuestionSeenService;
    private final InterviewQuestionRepository interviewQuestionRepository;

    @Transactional
    public Interview startInterview(SupabaseUser user, Question.Difficulty difficulty,
                                    Integer numQuestions, Integer timeMinutes) {
        log.info("Starting interview for user: {} with difficulty: {}, questions: {}, time: {}",
                user.getEmail(), difficulty, numQuestions, timeMinutes);

        List<Question> assignedQuestions =
                questionSelectionService.getRandomQuestionsForUser(user.getId(), difficulty, numQuestions);
        log.info("Assigned {} questions to interview", assignedQuestions.size());

        Interview interview = new Interview();
        interview.setUserId(user.getId());
        interview.setUserEmail(user.getEmail());
        interview.setDifficulty(difficulty);
        interview.setNumQuestions(numQuestions);
        interview.setTimeMinutes(timeMinutes);
        interview.setStartedAt(Instant.now());
        interview.setStatus(Interview.Status.IN_PROGRESS);

        List<InterviewQuestion> links = new ArrayList<>();
        int order = 1;
        for (Question q : assignedQuestions) {
            links.add(new InterviewQuestion(interview, q, order++));
        }
        interview.setAssignedQuestionLinks(links);

        Interview saved = interviewRepository.saveAndFlush(interview);
        saved.getAssignedQuestions().size();
        return saved;
    }

    /**
     * Load interview and enforce ownership. Throws 404 if missing, 403 if not owner.
     */
    public Interview requireOwnedInterview(UUID interviewId, String userId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Interview"));
        if (!interview.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this interview session.");
        }
        return interview;
    }

    /**
     * Complete an interview owned by {@code userId}.
     * Rejects non-owners (403), non-IN_PROGRESS (409), and unassigned questionIds (400).
     */
    @Transactional
    public Interview endInterview(UUID interviewId, String userId, List<SubmissionRequest> submissions) {
        log.info("Ending interview: {} with {} submissions for user {}", interviewId, submissions.size(), userId);

        Interview interview = requireOwnedInterview(interviewId, userId);

        if (interview.getStatus() != Interview.Status.IN_PROGRESS) {
            throw new ConflictException("This interview can no longer accept submissions.");
        }

        // Load assigned question IDs inside the same transaction before save
        Set<UUID> assignedQuestionIds = new HashSet<>(
                interviewQuestionRepository.findQuestionIdsByInterviewId(interviewId));

        for (SubmissionRequest submissionReq : submissions) {
            UUID qid = submissionReq.getQuestionId();
            if (qid == null || !assignedQuestionIds.contains(qid)) {
                throw new BadRequestException(
                        "Submission includes a question that is not part of this interview.");
            }
        }

        interview.setEndedAt(Instant.now());
        interview.setStatus(Interview.Status.COMPLETED);

        List<UUID> questionIds = submissions.stream().map(SubmissionRequest::getQuestionId).toList();
        List<Question> questions = questionRepository.findAllById(questionIds);
        Map<UUID, Question> questionById = new HashMap<>();
        for (Question q : questions) {
            questionById.put(q.getId(), q);
        }

        List<UserSubmission> submissionsToSave = new ArrayList<>();
        for (SubmissionRequest submissionReq : submissions) {
            Question question = questionById.get(submissionReq.getQuestionId());
            if (question == null) {
                throw ResourceNotFoundException.of("Question");
            }
            UserSubmission submission = new UserSubmission();
            submission.setInterview(interview);
            submission.setQuestion(question);
            submission.setCode(submissionReq.getCode());
            submission.setLanguage(submissionReq.getLanguage());
            submission.setUserTimeComplexity(submissionReq.getTimeComplexity());
            submission.setUserSpaceComplexity(submissionReq.getSpaceComplexity());
            submission.setSubmittedAt(Instant.now());
            submissionsToSave.add(submission);
        }

        userSubmissionRepository.saveAll(submissionsToSave);
        return interviewRepository.save(interview);
    }

    public void markQuestionsAsSeen(String userId, Interview interview) {
        userQuestionSeenService.markQuestionsAsSeen(userId, interview);
    }

    public void markQuestionsAsSeen(String userId, List<UUID> questionIds, Question.Difficulty difficulty) {
        userQuestionSeenService.markQuestionsAsSeen(userId, questionIds, difficulty);
    }

    public Interview getInterviewWithFeedback(UUID interviewId, String userId) {
        return requireOwnedInterview(interviewId, userId);
    }

    public List<UserSubmission> getSubmissionsWithFeedback(UUID interviewId, String userId) {
        requireOwnedInterview(interviewId, userId);
        return userSubmissionRepository.findByInterviewIdWithQuestionOrderBySubmittedAt(interviewId);
    }

    public Interview validateInterviewAccess(UUID interviewId, String userId) {
        log.info("Validating interview access: interviewId={}, userId={}", interviewId, userId);
        Interview interview = requireOwnedInterview(interviewId, userId);
        log.info("Interview access validated successfully for user: {}", userId);
        return interview;
    }

    public Interview findOngoingInterviewByUserId(String userId) {
        List<Interview> inProgressInterviews =
                interviewRepository.findByUserIdAndStatus(userId, Interview.Status.IN_PROGRESS);

        for (Interview interview : inProgressInterviews) {
            if (isInterviewStillOngoing(interview)) {
                return interview;
            }
            interview.setStatus(Interview.Status.ABANDONED);
            interview.setEndedAt(Instant.now());
            interviewRepository.save(interview);
            log.info("Interview {} expired and marked as abandoned", interview.getId());
        }
        return null;
    }

    private boolean isInterviewStillOngoing(Interview interview) {
        Instant now = Instant.now();
        Instant startTime = interview.getStartedAt();
        long durationMinutes = interview.getTimeMinutes();
        long elapsedMinutes = java.time.Duration.between(startTime, now).toMinutes();
        return elapsedMinutes < durationMinutes;
    }
}
