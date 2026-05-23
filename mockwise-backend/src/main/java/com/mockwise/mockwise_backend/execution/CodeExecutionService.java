package com.mockwise.mockwise_backend.execution;

/**
 * Service interface for compiling and executing user-submitted code.
 * <p>
 * Currently backed by {@link LocalCodeExecutionService} which runs code
 * as local OS processes. To move execution to a separate microservice,
 * create a remote implementation that delegates over HTTP and swap the
 * active Spring bean — no callers need to change.
 */
public interface CodeExecutionService {

    /**
     * Compiles and executes the given source code with default limits
     * (5-second timeout, 256 MB memory).
     */
    ExecutionResult execute(String code, SupportedLanguage language);

    /**
     * Compiles and executes the given source code with custom limits.
     */
    ExecutionResult execute(String code, SupportedLanguage language,
                            long timeoutSeconds, long memoryLimitMB);
}