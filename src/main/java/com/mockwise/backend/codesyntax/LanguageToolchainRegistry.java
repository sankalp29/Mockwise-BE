package com.mockwise.backend.codesyntax;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LanguageToolchainRegistry {

    private final Map<String, LanguageToolchain> byKey;
    private final List<LanguageToolchain> uniqueToolchains;

    public LanguageToolchainRegistry(List<LanguageToolchain> toolchains) {
        Map<String, LanguageToolchain> map = new HashMap<>();
        Map<String, LanguageToolchain> unique = new LinkedHashMap<>();
        for (LanguageToolchain tool : toolchains) {
            unique.putIfAbsent(tool.languageId(), tool);
            register(map, tool.languageId(), tool);
            for (String alias : tool.aliases()) {
                register(map, alias, tool);
            }
        }
        this.byKey = Map.copyOf(map);
        this.uniqueToolchains = List.copyOf(unique.values());
    }

    private static void register(Map<String, LanguageToolchain> map, String key, LanguageToolchain tool) {
        String normalized = normalize(key);
        LanguageToolchain existing = map.putIfAbsent(normalized, tool);
        if (existing != null && existing != tool) {
            throw new IllegalStateException(
                    "Duplicate language key '" + normalized + "' for "
                            + existing.getClass().getSimpleName() + " and "
                            + tool.getClass().getSimpleName());
        }
    }

    public Optional<LanguageToolchain> find(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(normalize(language)));
    }

    public LanguageToolchain require(String language) {
        return find(language).orElseThrow(() ->
                new IllegalArgumentException("Unsupported language for syntax checking: " + language));
    }

    public Set<String> supportedLanguageIds() {
        return uniqueToolchains.stream()
                .map(LanguageToolchain::languageId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Collection<LanguageToolchain> all() {
        return uniqueToolchains;
    }

    public static String normalize(String language) {
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
