package com.mockwise.backend.repository.question;

import com.mockwise.backend.repository.question.QuestionCodeStub;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionCodeStubRepository extends JpaRepository<QuestionCodeStub, UUID> {
    Optional<QuestionCodeStub> findFirstByQuestion_IdAndLanguageIgnoreCase(UUID questionId, String language);
}
