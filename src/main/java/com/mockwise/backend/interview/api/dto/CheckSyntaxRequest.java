package com.mockwise.backend.interview.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CheckSyntaxRequest {
    private String code;
    private String language;
}
