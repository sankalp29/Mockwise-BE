package com.mockwise.backend.repository.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByDifficulty(Difficulty difficulty);

    java.util.Optional<Question> findFirstByTitleIgnoreCase(String title);

    @Query(value = "SELECT * FROM questions WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit",
           nativeQuery = true)
    List<Question> findRandomQuestionsByDifficulty(@Param("difficulty") String difficulty, @Param("limit") int limit);

    @Query(value = "SELECT * FROM questions WHERE difficulty = :difficulty AND id NOT IN :excludeIds ORDER BY RANDOM() LIMIT :limit",
           nativeQuery = true)
    List<Question> findRandomQuestionsByDifficultyExcluding(@Param("difficulty") String difficulty,
                                                           @Param("excludeIds") List<UUID> excludeIds,
                                                           @Param("limit") int limit);

    @Query("SELECT q FROM Question q WHERE q.difficulty = :difficulty AND q.id NOT IN :excludeIds")
    List<Question> findByDifficultyExcluding(@Param("difficulty") Difficulty difficulty,
                                            @Param("excludeIds") List<UUID> excludeIds);
}
