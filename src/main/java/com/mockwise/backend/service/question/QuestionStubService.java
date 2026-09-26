package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.QuestionCodeStub;
import com.mockwise.backend.repository.question.QuestionCodeStubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionStubService {

    private final QuestionCodeStubRepository questionCodeStubRepository;

    public Optional<String> stubFor(UUID questionId, String language) {
        return questionCodeStubRepository
                .findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, language)
                .map(QuestionCodeStub::getStub);
    }
}
