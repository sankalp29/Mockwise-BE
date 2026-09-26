package com.mockwise.backend.service.codesyntax;

import com.mockwise.backend.repository.question.ProgrammingLanguage;
import com.mockwise.backend.service.codesyntax.model.SyntaxCheckResult;

import java.nio.file.Path;
import java.util.Set;

/**
 * Strategy for one programming language's syntax check (and later compile).
 */
public interface LanguageToolchain {

    ProgrammingLanguage language();

    /** Canonical id, e.g. {@code java}, {@code python}, {@code cpp}. */
    default String languageId() {
        return language().id();
    }

    /** Alternate spellings accepted by the API, e.g. {@code c++}, {@code js}. */
    default Set<String> aliases() {
        return language().aliases();
    }

    /**
     * Best-effort probe: whether the local toolchain looks installed.
     * Used by {@code GET /api/codesyntax/languages}; may be approximate.
     */
    default boolean isToolchainAvailable() {
        return true;
    }

    /** Display name for UI. */
    default String displayName() {
        return language().displayName();
    }

    SyntaxCheckResult checkSyntax(String code, Path workDir);
}
