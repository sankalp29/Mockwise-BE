package com.mockwise.backend.service.question;

import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.progress.UserQuestionSeenRepository;
import com.mockwise.backend.repository.question.Question;
import com.mockwise.backend.repository.question.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSelectionServiceTest {

    @Mock private QuestionRepository questionRepository;
    @Mock private UserQuestionSeenRepository userQuestionSeenRepository;
    @InjectMocks private QuestionSelectionService questionSelectionService;

    @Test
    void fallsBackToDifficultyLookupWhenTheRandomQueryFails() {
        Question question = new Question();
        question.setTitle("Two Sum");
        when(questionRepository.findRandomQuestionsByDifficulty("EASY", 1))
                .thenThrow(new RuntimeException("random unavailable"));
        when(questionRepository.findByDifficulty(Difficulty.EASY)).thenReturn(List.of(question));

        List<Question> selected = questionSelectionService.getRandomQuestionsForUser(
                null, Difficulty.EASY, 1);

        assertEquals(List.of(question), selected);
        verify(userQuestionSeenRepository, never()).findSeenQuestionIdsByUserAndDifficulty(any(), any());
    }

    @Test
    void clearsSeenQuestionsWhenTooFewRemainUnseen() {
        UUID seenId = UUID.randomUUID();
        Question fresh = new Question();
        fresh.setTitle("Fresh");
        when(userQuestionSeenRepository.findSeenQuestionIdsByUserAndDifficulty("user-1", Difficulty.MEDIUM))
                .thenReturn(List.of(seenId));
        when(userQuestionSeenRepository.countTotalQuestionsByDifficulty(Difficulty.MEDIUM)).thenReturn(1L);
        when(questionRepository.findRandomQuestionsByDifficulty(eq("MEDIUM"), eq(1))).thenReturn(List.of(fresh));

        List<Question> selected = questionSelectionService.getRandomQuestionsForUser(
                "user-1", Difficulty.MEDIUM, 1);

        assertEquals(List.of(fresh), selected);
        verify(userQuestionSeenRepository).deleteByUserIdAndDifficulty("user-1", Difficulty.MEDIUM);
        verify(questionRepository, never()).findRandomQuestionsByDifficultyExcluding(any(), anyList(), anyInt());
    }
}
