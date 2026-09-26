package com.mockwise.backend.repository.question;

import java.util.Locale;

/**
 * Shared difficulty for coding questions and system-design prompts.
 * Stored as {@code EASY}, {@code MEDIUM}, or {@code HARD}.
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    public String label() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
