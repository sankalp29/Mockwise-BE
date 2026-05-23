package com.mockwise.mockwise_backend.execution;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExecutionResult {
    private final ExecutionStatus status;
    private final String stdout;
    private final String stderr;
    private final long executionTimeMs;
    private final int exitCode;
    private final String errorMessage;
}