package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    
    List<Question> findByDifficulty(Question.Difficulty difficulty);

    // Method to fetch only question IDs by difficulty
    @Query("SELECT q.id FROM Question q WHERE q.difficulty = :difficulty")
    List<UUID> findIdsByDifficulty(@Param("difficulty") Question.Difficulty difficulty);
    
    @Query(value = "SELECT * FROM questions WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit", 
           nativeQuery = true)
    List<Question> findRandomQuestionsByDifficulty(@Param("difficulty") String difficulty, @Param("limit") int limit);
    
    // Fallback method without RANDOM() in case of issues
    @Query("SELECT q FROM Question q WHERE q.difficulty = :difficulty")
    List<Question> findByDifficultyString(@Param("difficulty") Question.Difficulty difficulty);

    @EntityGraph(attributePaths = {"testCases", "driverCodeTemplates", "codeStubTemplates"})
    Optional<Question> findById(UUID id);
}
