package com.mockwise.backend.repository.question;

import com.mockwise.backend.repository.question.OptimalSolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OptimalSolutionRepository extends JpaRepository<OptimalSolution, UUID> {

    @Query("select o from OptimalSolution o where o.question.id = :questionId and lower(o.language) = lower(:language)")
    Optional<OptimalSolution> findByQuestionIdAndLanguage(@Param("questionId") UUID questionId,
                                                          @Param("language") String language);
}
