package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;

import java.nio.file.Path;
import java.util.Set;

/**
 * Strategy for one programming language's syntax check (and later compile).
 */
public interface LanguageToolchain {

    /** Canonical id, e.g. {@code java}, {@code python}, {@code cpp}. */
    String languageId();

    /** Alternate spellings accepted by the API, e.g. {@code c++}, {@code js}. */
    Set<String> aliases();

    /**
     * Best-effort probe: whether the local toolchain looks installed.
     * Used by {@code GET /supported-languages}; may be approximate.
     */
    default boolean isToolchainAvailable() {
        return true;
    }

    /** Display name for UI. */
    default String displayName() {
        return languageId();
    }

    SyntaxCheckResult checkSyntax(String code, Path workDir);
}
