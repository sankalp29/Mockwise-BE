package com.mockwise.mockwise_backend.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for language-specific compilation and execution.
 * Implement this interface and annotate with @Component to add support
 * for a new language — no changes needed in existing classes.
 */
public interface LanguageExecutor {

    SupportedLanguage getLanguage();

    /**
     * Compiles the source code in the given work directory.
     * Returns an empty list on success, or a list of error messages on failure.
     */
    List<String> compile(String sourceCode, Path workDir) throws IOException, InterruptedException;

    /**
     * Builds the shell command to execute the compiled code.
     * The command should include memory-limiting flags appropriate for this language.
     */
    String[] buildExecutionCommand(Path workDir, long memoryLimitMB);

    /**
     * Returns the security rules (forbidden code patterns) for this language.
     */
    List<SecurityRule> getSecurityRules();
}