package com.mockwise.backend.codesyntax.model;

import java.util.List;

/**
 * Structured syntax-check outcome. {@link #messages()} preserves the legacy
 * flat list used by the HTTP API.
 */
public record SyntaxCheckResult(
        boolean success,
        List<String> messages,
        ToolStatus toolStatus
) {
    public static SyntaxCheckResult ok() {
        return new SyntaxCheckResult(true, List.of(), ToolStatus.AVAILABLE);
    }

    public static SyntaxCheckResult syntaxErrors(List<String> messages) {
        return new SyntaxCheckResult(false, List.copyOf(messages), ToolStatus.AVAILABLE);
    }

    public static SyntaxCheckResult toolMissing(String message) {
        return new SyntaxCheckResult(false, List.of(message), ToolStatus.MISSING);
    }

    public static SyntaxCheckResult failed(String message) {
        return new SyntaxCheckResult(false, List.of(message), ToolStatus.FAILED);
    }

    public static SyntaxCheckResult unsupported(String language) {
        return new SyntaxCheckResult(
                false,
                List.of("Unsupported language for syntax checking: " + language),
                ToolStatus.MISSING);
    }
}
