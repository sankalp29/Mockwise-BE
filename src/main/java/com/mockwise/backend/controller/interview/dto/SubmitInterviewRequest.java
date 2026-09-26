package com.mockwise.backend.controller.interview.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SubmitInterviewRequest {
    private List<SubmissionRequest> submissions;
}
