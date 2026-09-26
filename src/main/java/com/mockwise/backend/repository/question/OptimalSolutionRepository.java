package com.mockwise.backend.repository.question;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OptimalSolutionRepository extends JpaRepository<OptimalSolution, UUID> {

    @Query("select o from OptimalSolution o where o.question.id = :questionId and o.language = :language")
    Optional<OptimalSolution> findByQuestionIdAndLanguage(@Param("questionId") UUID questionId,
                                                          @Param("language") ProgrammingLanguage language);
}
