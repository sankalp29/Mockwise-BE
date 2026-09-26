package com.mockwise.backend.service.interview;

import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.exception.ConflictException;
import com.mockwise.backend.exception.ForbiddenException;
import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.repository.interview.Interview;
import com.mockwise.backend.repository.interview.InterviewQuestion;
import com.mockwise.backend.repository.interview.InterviewQuestionRepository;
import com.mockwise.backend.repository.interview.InterviewRepository;
import com.mockwise.backend.service.progress.UserQuestionSeenService;
import com.mockwise.backend.service.question.QuestionSelectionService;
import com.mockwise.backend.service.session.ActiveSessionGuard;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionRepository;
import com.mockwise.backend.repository.submission.UserSubmission;
import com.mockwise.backend.repository.submission.UserSubmissionRepository;
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
    private final ActiveSessionGuard activeSessionGuard;

    @Transactional
    public Interview startInterview(SupabaseUser user, Question.Difficulty difficulty,
                                    Integer numQuestions, Integer timeMinutes) {
        activeSessionGuard.requireClear(user.getId());
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

        List<InterviewQuestion> assignedLinks = new ArrayList<>();
        int order = 1;
        for (Question question : assignedQuestions) {
            assignedLinks.add(new InterviewQuestion(interview, question, order++));
        }
        interview.setAssignedQuestionLinks(assignedLinks);

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
    public Interview endInterview(UUID interviewId, String userId, List<SubmittedSolution> submissions) {
        log.info("Ending interview: {} with {} submissions for user {}", interviewId, submissions.size(), userId);

        Interview interview = requireOwnedInterview(interviewId, userId);

        if (interview.getStatus() != Interview.Status.IN_PROGRESS) {
            throw new ConflictException("This interview can no longer accept submissions.");
        }

        // Load assigned question IDs inside the same transaction before save
        Set<UUID> assignedQuestionIds = new HashSet<>(
                interviewQuestionRepository.findQuestionIdsByInterviewId(interviewId));

        for (SubmittedSolution requested : submissions) {
            UUID questionId = requested.questionId();
            if (questionId == null || !assignedQuestionIds.contains(questionId)) {
                throw new BadRequestException(
                        "Submission includes a question that is not part of this interview.");
            }
        }

        interview.setEndedAt(Instant.now());
        interview.setStatus(Interview.Status.COMPLETED);

        List<UUID> questionIds = submissions.stream().map(SubmittedSolution::questionId).toList();
        List<Question> questions = questionRepository.findAllById(questionIds);
        Map<UUID, Question> questionById = new HashMap<>();
        for (Question q : questions) {
            questionById.put(q.getId(), q);
        }

        List<UserSubmission> submissionsToSave = new ArrayList<>();
        for (SubmittedSolution requested : submissions) {
            Question question = questionById.get(requested.questionId());
            if (question == null) {
                throw ResourceNotFoundException.of("Question");
            }
            UserSubmission submission = new UserSubmission();
            submission.setInterview(interview);
            submission.setQuestion(question);
            submission.setCode(requested.code());
            submission.setLanguage(requested.language());
            submission.setUserTimeComplexity(requested.timeComplexity());
            submission.setUserSpaceComplexity(requested.spaceComplexity());
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
