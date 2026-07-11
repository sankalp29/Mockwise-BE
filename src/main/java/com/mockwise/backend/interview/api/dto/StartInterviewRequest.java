package com.mockwise.backend.interview.api.dto;

import com.mockwise.backend.question.domain.Question;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartInterviewRequest {
    private Question.Difficulty difficulty;
    private Integer numQuestions;
    private Integer timeMinutes;
}
