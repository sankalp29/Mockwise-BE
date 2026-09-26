package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.progress.UserQuestionSeenRepository;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionRepository;
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

        List<Question> questions = selectQuestions(difficulty, seenQuestionIds, count);
        log.info("Found {} questions (excluding seen)", questions.size());
        return questions;
    }

    private List<Question> getRandomQuestionsOriginal(Question.Difficulty difficulty, int count) {
        log.info("Total questions in database: {}", questionRepository.count());
        List<Question> questions = selectQuestions(difficulty, List.of(), count);
        log.info("Found {} questions", questions.size());
        return questions;
    }

    private List<Question> selectQuestions(Question.Difficulty difficulty, List<UUID> excluded, int count) {
        try {
            if (excluded.isEmpty()) {
                return questionRepository.findRandomQuestionsByDifficulty(difficulty.name(), count);
            }
            return questionRepository.findRandomQuestionsByDifficultyExcluding(difficulty.name(), excluded, count);
        } catch (RuntimeException failure) {
            log.warn("Random query failed, using fallback: ", failure);
            if (excluded.isEmpty()) {
                return questionRepository.findByDifficulty(difficulty).stream().limit(count).toList();
            }
            return questionRepository.findByDifficultyExcluding(difficulty, excluded).stream().limit(count).toList();
        }
    }
}
