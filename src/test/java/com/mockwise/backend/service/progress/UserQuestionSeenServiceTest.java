package com.mockwise.backend.service.progress;

import com.mockwise.backend.repository.progress.UserQuestionSeen;
import com.mockwise.backend.repository.progress.UserQuestionSeenRepository;
import com.mockwise.backend.repository.question.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQuestionSeenServiceTest {

    @Mock private UserQuestionSeenRepository userQuestionSeenRepository;
    @InjectMocks private UserQuestionSeenService userQuestionSeenService;

    @Test
    void doesNothingWhenThereAreNoQuestions() {
        userQuestionSeenService.markQuestionsAsSeen("user-1", List.of(), Question.Difficulty.EASY);

        verify(userQuestionSeenRepository, never()).saveAll(anyList());
    }

    @Test
    void storesOnlyQuestionsTheUserHasNotSeen() {
        UUID alreadySeen = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(userQuestionSeenRepository.findSeenQuestionIdsByUserAndQuestionIds(
                "user-1", List.of(alreadySeen, fresh))).thenReturn(List.of(alreadySeen));

        userQuestionSeenService.markQuestionsAsSeen(
                "user-1", List.of(alreadySeen, fresh), Question.Difficulty.HARD);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserQuestionSeen>> saved = ArgumentCaptor.forClass(List.class);
        verify(userQuestionSeenRepository).saveAll(saved.capture());
        assertEquals(1, saved.getValue().size());
        assertEquals(fresh, saved.getValue().get(0).getQuestionId());
        assertEquals(Question.Difficulty.HARD, saved.getValue().get(0).getDifficulty());
    }
}
