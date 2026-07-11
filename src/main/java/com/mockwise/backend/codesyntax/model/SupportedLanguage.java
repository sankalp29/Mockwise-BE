package com.mockwise.backend.codesyntax.model;

import java.util.List;

public record SupportedLanguage(
        String id,
        String displayName,
        List<String> aliases,
        boolean syntaxCheckAvailable
) {}
