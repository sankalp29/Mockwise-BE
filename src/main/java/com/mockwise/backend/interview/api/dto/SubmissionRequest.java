package com.mockwise.backend.interview.api.dto;

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
    private String language;
    private String timeComplexity;
    private String spaceComplexity;

    public SubmissionRequest(UUID questionId, String code, String language) {
        this.questionId = questionId;
        this.code = code;
        this.language = language;
    }
}
