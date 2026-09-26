package com.mockwise.backend.repository.question;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionCodeStubRepository extends JpaRepository<QuestionCodeStub, UUID> {
    Optional<QuestionCodeStub> findFirstByQuestion_IdAndLanguage(UUID questionId, ProgrammingLanguage language);
}
