package com.mockwise.backend.question.application;

import com.mockwise.backend.progress.infrastructure.UserQuestionSeenRepository;
import com.mockwise.backend.question.domain.Question;
import com.mockwise.backend.question.infrastructure.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionSelectionService {

    private final QuestionRepository questionRepository;
    private final UserQuestionSeenRepository userQuestionSeenRepository;

    public List<Question> getRandomQuestionsForUser(String userId, Question.Difficulty difficulty, int count) {
        log.info("Requesting {} random questions with difficulty: {} for user: {}", count, difficulty, userId);
        if (userId != null) {
            return getRandomQuestionsExcludingSeen(userId, difficulty, count);
        }
        return getRandomQuestionsOriginal(difficulty, count);
    }

    private List<Question> getRandomQuestionsExcludingSeen(String userId, Question.Difficulty difficulty, int count) {
        List<UUID> seenQuestionIds = userQuestionSeenRepository.findSeenQuestionIdsByUserAndDifficulty(userId, difficulty);
        log.info("User {} has seen {} questions of difficulty {}", userId, seenQuestionIds.size(), difficulty);

        long totalCount = userQuestionSeenRepository.countTotalQuestionsByDifficulty(difficulty);
        long unseenCount = totalCount - seenQuestionIds.size();

        if (unseenCount < count) {
            log.info("User {} has only {} unseen questions but requested {}. Resetting seen for difficulty {}.",
                    userId, unseenCount, count, difficulty);
            userQuestionSeenRepository.deleteByUserIdAndDifficulty(userId, difficulty);
            seenQuestionIds = new ArrayList<>();
        }

        List<Question> questions;
        try {
            if (seenQuestionIds.isEmpty()) {
                questions = questionRepository.findRandomQuestionsByDifficulty(difficulty.name(), count);
            } else {
                questions = questionRepository.findRandomQuestionsByDifficultyExcluding(difficulty.name(), seenQuestionIds, count);
            }
            log.info("Found {} questions using random query (excluding seen)", questions.size());
        } catch (Exception e) {
            log.warn("Random query failed, using fallback: ", e);
            if (seenQuestionIds.isEmpty()) {
                questions = questionRepository.findByDifficultyString(difficulty).stream().limit(count).toList();
            } else {
                questions = questionRepository.findByDifficultyExcluding(difficulty, seenQuestionIds)
                        .stream().limit(count).toList();
            }
            log.info("Found {} questions using fallback query (excluding seen)", questions.size());
        }
        return questions;
    }

    private List<Question> getRandomQuestionsOriginal(Question.Difficulty difficulty, int count) {
        log.info("Total questions in database: {}", questionRepository.count());
        try {
            List<Question> questions = questionRepository.findRandomQuestionsByDifficulty(difficulty.name(), count);
            log.info("Found {} questions using random query", questions.size());
            return questions;
        } catch (Exception e) {
            log.warn("Random query failed, using fallback: ", e);
            List<Question> questions = questionRepository.findByDifficultyString(difficulty).stream().limit(count).toList();
            log.info("Found {} questions using fallback query", questions.size());
            return questions;
        }
    }
}
