package com.mockwise.backend.controller.interview.dto;

import com.mockwise.backend.repository.question.ProgrammingLanguage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SubmissionRequest {
    private UUID questionId;
    private String code;
    private ProgrammingLanguage language;
    private String timeComplexity;
    private String spaceComplexity;

    public SubmissionRequest(UUID questionId, String code, ProgrammingLanguage language) {
        this.questionId = questionId;
        this.code = code;
        this.language = language;
    }
}
