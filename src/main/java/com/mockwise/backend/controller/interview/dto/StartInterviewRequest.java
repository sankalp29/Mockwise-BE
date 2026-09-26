package com.mockwise.backend.controller.interview.dto;

import com.mockwise.backend.repository.question.Difficulty;
import com.mockwise.backend.repository.question.Question;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartInterviewRequest {
    private Difficulty difficulty;
    private Integer numQuestions;
    private Integer timeMinutes;
}
