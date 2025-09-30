package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserQuestionSeenRepository extends JpaRepository<UserQuestionSeen, UUID> {
    
    // Find seen question IDs for a user and difficulty
    @Query("SELECT uqs.questionId FROM UserQuestionSeen uqs WHERE uqs.userId = :userId AND uqs.difficulty = :difficulty")
    List<UUID> findSeenQuestionIdsByUserAndDifficulty(@Param("userId") String userId, @Param("difficulty") Question.Difficulty difficulty);
    
    // Check if user has seen all questions of a difficulty
    @Query("SELECT COUNT(DISTINCT uqs.questionId) FROM UserQuestionSeen uqs WHERE uqs.userId = :userId AND uqs.difficulty = :difficulty")
    long countSeenQuestionsByUserAndDifficulty(@Param("userId") String userId, @Param("difficulty") Question.Difficulty difficulty);
    
    // Get total questions count for a difficulty
    @Query("SELECT COUNT(q) FROM Question q WHERE q.difficulty = :difficulty")
    long countTotalQuestionsByDifficulty(@Param("difficulty") Question.Difficulty difficulty);
    
    // Delete all seen questions for a user and difficulty (reset functionality)
    @Modifying
    @Query("DELETE FROM UserQuestionSeen uqs WHERE uqs.userId = :userId AND uqs.difficulty = :difficulty")
    void deleteByUserIdAndDifficulty(@Param("userId") String userId, @Param("difficulty") Question.Difficulty difficulty);
    
    // Check if a specific question is seen by user
    boolean existsByUserIdAndQuestionId(String userId, UUID questionId);
    
    // Batch check for multiple questions
    @Query("SELECT uqs.questionId FROM UserQuestionSeen uqs WHERE uqs.userId = :userId AND uqs.questionId IN :questionIds")
    List<UUID> findSeenQuestionIdsByUserAndQuestionIds(@Param("userId") String userId, @Param("questionIds") List<UUID> questionIds);
    
    // Batch insert using native query
    @Modifying
    @Query(value = """
        INSERT INTO user_question_seen (user_id, question_id, difficulty, seen_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (user_id, question_id) DO NOTHING
        """, nativeQuery = true)
    void insertSeenQuestionsBatch(String userId, UUID questionId, String difficulty);
}
