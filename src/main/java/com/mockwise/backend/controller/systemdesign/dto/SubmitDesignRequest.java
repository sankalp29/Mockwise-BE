package com.mockwise.backend.controller.systemdesign.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitDesignRequest {
    private String scene;
    private String caption;
}
