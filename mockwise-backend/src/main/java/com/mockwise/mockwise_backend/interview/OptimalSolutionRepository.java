package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OptimalSolutionRepository extends JpaRepository<OptimalSolution, UUID> {

    @Query("select o from OptimalSolution o where o.question.id = :questionId and lower(o.language) = lower(:language)")
    Optional<OptimalSolution> findByQuestionIdAndLanguage(@Param("questionId") UUID questionId,
                                                          @Param("language") String language);
}


