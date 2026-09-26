package com.mockwise.backend.repository.dashboard;

import java.util.Locale;

/**
 * Dashboard query: every practice, or one of {@link Practice}.
 */
public enum PracticeFilter {
    ALL,
    CODING,
    SYSTEM_DESIGN;

    public Practice practice() {
        if (this == ALL) {
            return null;
        }
        return Practice.valueOf(name());
    }

    public static PracticeFilter parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Practice must be ALL, CODING, or SYSTEM_DESIGN.");
        }
    }
}
