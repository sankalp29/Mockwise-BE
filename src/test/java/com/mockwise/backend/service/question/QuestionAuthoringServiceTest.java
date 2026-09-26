package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.exception.BadRequestException;
import com.mockwise.backend.repository.question.OptimalSolutionRepository;
import com.mockwise.backend.repository.question.ProgrammingLanguage;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionCodeStubRepository;
import com.mockwise.backend.repository.question.QuestionRepository;
import com.mockwise.backend.service.codesyntax.LanguageToolchain;
import com.mockwise.backend.service.codesyntax.LanguageToolchainRegistry;
import com.mockwise.backend.service.codesyntax.SyntaxCheckFacade;
import com.mockwise.backend.service.codesyntax.model.SyntaxCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAuthoringServiceTest {

    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionCodeStubRepository questionCodeStubRepository;
    @Mock private OptimalSolutionRepository optimalSolutionRepository;
    @Mock private LanguageToolchainRegistry languageToolchainRegistry;
    @Mock private SyntaxCheckFacade syntaxCheckFacade;
    @Mock private LanguageToolchain javaToolchain;

    private QuestionAuthoringService service;

    @BeforeEach
    void setUp() {
        service = new QuestionAuthoringService(
                questionRepository,
                questionCodeStubRepository,
                optimalSolutionRepository,
                languageToolchainRegistry,
                syntaxCheckFacade);
        when(javaToolchain.language()).thenReturn(ProgrammingLanguage.JAVA);
        when(languageToolchainRegistry.all()).thenReturn(List.of(javaToolchain));
    }

    @Test
    void rejectsAStubThatFailsSyntaxCheckAndSavesNothing() {
        when(syntaxCheckFacade.check("java", "class Solution {}")).thenReturn(SyntaxCheckResult.ok());
        when(syntaxCheckFacade.check("java", "class Solution { int x = ; }"))
                .thenReturn(SyntaxCheckResult.syntaxErrors(List.of("';' expected")));

        NewQuestion request = new NewQuestion(
                "Add",
                "## Add two numbers",
                "`1 + 1 = 2`",
                "- positive integers",
                Difficulty.EASY,
                List.of(new NewQuestion.LanguageCode(ProgrammingLanguage.JAVA, "class Solution {}")),
                List.of(new NewQuestion.LanguageCode(ProgrammingLanguage.JAVA, "class Solution { int x = ; }")));

        BadRequestException failure = assertThrows(BadRequestException.class, () -> service.create(request));
        assertEquals(true, failure.getClientMessage().contains("optimal code failed syntax check"));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void rejectsAQuestionThatOmitsALanguage() {
        NewQuestion request = new NewQuestion(
                "Add",
                "## Add",
                "example",
                "constraints",
                Difficulty.EASY,
                List.of(),
                List.of(new NewQuestion.LanguageCode(ProgrammingLanguage.JAVA, "class Solution {}")));

        BadRequestException failure = assertThrows(BadRequestException.class, () -> service.create(request));
        assertEquals(true, failure.getClientMessage().contains("Missing code stub for java"));
        verify(syntaxCheckFacade, never()).check(any(), any());
    }

    @Test
    void savesWhenEveryLanguageIsSyntacticallyValid() {
        when(syntaxCheckFacade.check("java", "class Solution {}")).thenReturn(SyntaxCheckResult.ok());
        when(questionRepository.save(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(UUID.randomUUID());
            return question;
        });

        NewQuestion request = new NewQuestion(
                "Add",
                "## Add two numbers",
                "`1 + 1 = 2`",
                "- positive integers",
                Difficulty.EASY,
                List.of(new NewQuestion.LanguageCode(ProgrammingLanguage.JAVA, "class Solution {}")),
                List.of(new NewQuestion.LanguageCode(ProgrammingLanguage.JAVA, "class Solution {}")));

        Question saved = service.create(request);
        assertEquals("Add", saved.getTitle());
        assertEquals(Difficulty.EASY, saved.getDifficulty());
        verify(questionCodeStubRepository).save(any());
        verify(optimalSolutionRepository).save(any());
    }
}
