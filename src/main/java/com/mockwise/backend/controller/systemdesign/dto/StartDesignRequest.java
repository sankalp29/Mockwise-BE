package com.mockwise.backend.controller.systemdesign.dto;

import com.mockwise.backend.repository.question.Difficulty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartDesignRequest {
    private Difficulty difficulty;
    private Integer timeMinutes;
}
