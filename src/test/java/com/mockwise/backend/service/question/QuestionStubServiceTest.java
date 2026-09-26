package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.QuestionCodeStub;
import com.mockwise.backend.repository.question.QuestionCodeStubRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionStubServiceTest {

    @Mock private QuestionCodeStubRepository questionCodeStubRepository;
    @InjectMocks private QuestionStubService questionStubService;

    @Test
    void returnsTheStubForTheRequestedLanguage() {
        UUID questionId = UUID.randomUUID();
        QuestionCodeStub stub = new QuestionCodeStub();
        stub.setStub("class Solution {}");
        when(questionCodeStubRepository.findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, "java"))
                .thenReturn(Optional.of(stub));

        assertEquals(Optional.of("class Solution {}"), questionStubService.stubFor(questionId, "java"));
    }

    @Test
    void returnsEmptyWhenNoStubExists() {
        UUID questionId = UUID.randomUUID();
        when(questionCodeStubRepository.findFirstByQuestion_IdAndLanguageIgnoreCase(questionId, "python"))
                .thenReturn(Optional.empty());

        assertTrue(questionStubService.stubFor(questionId, "python").isEmpty());
    }
}
