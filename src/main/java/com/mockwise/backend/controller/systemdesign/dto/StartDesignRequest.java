package com.mockwise.backend.controller.systemdesign.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StartDesignRequest {
    private String difficulty;
    private Integer timeMinutes;
}
