package com.mockwise.backend.progress.application;

import com.mockwise.backend.interview.domain.Interview;
import com.mockwise.backend.progress.domain.UserQuestionSeen;
import com.mockwise.backend.progress.infrastructure.UserQuestionSeenRepository;
import com.mockwise.backend.question.domain.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserQuestionSeenService {

    private final UserQuestionSeenRepository userQuestionSeenRepository;

    @Transactional
    public void markQuestionsAsSeen(String userId, Interview interview) {
        // Prefer IDs already on the entity graph; fall back to difficulty + empty if links not loaded.
        List<UUID> assignedQuestionIds;
        try {
            assignedQuestionIds = interview.getAssignedQuestionLinks() == null
                    ? List.of()
                    : interview.getAssignedQuestionLinks().stream()
                    .map(link -> link.getQuestion().getId())
                    .toList();
        } catch (Exception lazy) {
            log.warn("Could not read assignedQuestionLinks (lazy); skipping seen-mark for interview {}",
                    interview.getId());
            return;
        }
        markQuestionsAsSeen(userId, assignedQuestionIds, interview.getDifficulty());
    }

    @Transactional
    public void markQuestionsAsSeen(String userId, List<UUID> questionIds, Question.Difficulty difficulty) {
        log.info("Marking {} questions as seen for user: {}", questionIds.size(), userId);
        try {
            if (questionIds == null || questionIds.isEmpty()) {
                log.info("No question IDs to mark as seen for user: {}", userId);
                return;
            }

            List<UUID> existingSeen = userQuestionSeenRepository
                    .findSeenQuestionIdsByUserAndQuestionIds(userId, questionIds);
            Set<UUID> existingSeenSet = new HashSet<>(existingSeen);

            List<UserQuestionSeen> toInsert = questionIds.stream()
                    .filter(qid -> !existingSeenSet.contains(qid))
                    .map(qid -> new UserQuestionSeen(userId, qid, difficulty))
                    .toList();

            if (!toInsert.isEmpty()) {
                userQuestionSeenRepository.saveAll(toInsert);
                log.info("Inserted {} new seen records for user: {}", toInsert.size(), userId);
            } else {
                log.info("All {} questions already marked seen for user: {}", questionIds.size(), userId);
            }
        } catch (Exception e) {
            log.error("Error marking questions as seen for user: {}", userId, e);
        }
    }

    @Async
    public CompletableFuture<Void> markQuestionsAsSeenAsync(String userId, List<UUID> questionIds,
                                                            Question.Difficulty difficulty) {
        try {
            log.info("Async marking {} questions as seen for user: {}", questionIds.size(), userId);
            List<UserQuestionSeen> seenQuestions = questionIds.stream()
                    .filter(questionId -> !userQuestionSeenRepository.existsByUserIdAndQuestionId(userId, questionId))
                    .map(questionId -> new UserQuestionSeen(userId, questionId, difficulty))
                    .toList();
            if (!seenQuestions.isEmpty()) {
                userQuestionSeenRepository.saveAll(seenQuestions);
                log.info("Async marked {} questions as seen for user: {}", seenQuestions.size(), userId);
            }
        } catch (Exception e) {
            log.error("Error in async marking questions as seen for user: {}", userId, e);
        }
        return CompletableFuture.completedFuture(null);
    }
}
