package com.mockwise.mockwise_backend.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionDriverTemplateRepository extends JpaRepository<QuestionDriverTemplate, UUID> {
    Optional<QuestionDriverTemplate> findFirstByQuestion_IdAndLanguageIgnoreCase(UUID questionId, String language);
}


