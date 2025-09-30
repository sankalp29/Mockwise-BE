package com.mockwise.mockwise_backend.interview;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserQuestionSeenService {
    
    private final UserQuestionSeenRepository userQuestionSeenRepository;
    
    /**
     * Async method to mark questions as seen without blocking main transaction
     */
    @Async
    public CompletableFuture<Void> markQuestionsAsSeenAsync(String userId, List<UUID> questionIds, Question.Difficulty difficulty) {
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
