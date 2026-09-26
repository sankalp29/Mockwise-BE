package com.mockwise.backend.service.question;

import com.mockwise.backend.exception.ResourceNotFoundException;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock private QuestionRepository questionRepository;
    @InjectMocks private QuestionService questionService;

    @Test
    void returnsTheQuestionWhenItExists() {
        UUID questionId = UUID.randomUUID();
        Question question = new Question();
        question.setId(questionId);
        question.setTitle("Two Sum");
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertEquals(question, questionService.getById(questionId));
    }

    @Test
    void rejectsAMissingQuestion() {
        UUID questionId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        ResourceNotFoundException failure = assertThrows(
                ResourceNotFoundException.class,
                () -> questionService.getById(questionId));
        assertEquals("Question was not found.", failure.getMessage());
    }
}
