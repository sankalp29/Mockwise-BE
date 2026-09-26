package com.mockwise.backend.controller.systemdesign.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubmitDesignRequest {
    private JsonNode scene;
    private String caption;
}
