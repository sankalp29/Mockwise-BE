package com.mockwise.mockwise_backend.execution;

public enum ExecutionStatus {
    SUCCESS,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    TIMEOUT,
    MEMORY_LIMIT_EXCEEDED,
    SECURITY_VIOLATION
}