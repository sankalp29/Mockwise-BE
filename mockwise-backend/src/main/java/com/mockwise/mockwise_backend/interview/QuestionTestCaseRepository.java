package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionTestCaseRepository extends JpaRepository<QuestionTestCase, UUID> {
    List<QuestionTestCase> findByQuestionIdAndEnabledOrderById(UUID questionId, boolean enabled);
}


