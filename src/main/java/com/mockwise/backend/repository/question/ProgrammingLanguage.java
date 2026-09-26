package com.mockwise.backend.repository.question;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Languages a candidate can submit. The {@link #id()} is the value stored in the database
 * and sent on the wire. Aliases accept the spellings the syntax-check API already allowed.
 */
public enum ProgrammingLanguage {
    JAVA("java", "Java"),
    PYTHON("python", "Python", "py", "python3"),
    CPP("cpp", "C++", "c++", "cplusplus"),
    JAVASCRIPT("javascript", "JavaScript", "js", "node"),
    TYPESCRIPT("typescript", "TypeScript", "ts"),
    GO("go", "Go", "golang"),
    RUST("rust", "Rust", "rs"),
    RUBY("ruby", "Ruby", "rb"),
    SCALA("scala", "Scala"),
    CSHARP("csharp", "C#", "cs", "c#");

    private static final Map<String, ProgrammingLanguage> BY_KEY = index();

    private final String id;
    private final String displayName;
    private final Set<String> aliases;

    ProgrammingLanguage(String id, String displayName, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.aliases = Set.of(aliases);
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Set<String> aliases() {
        return aliases;
    }

    public static Optional<ProgrammingLanguage> find(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEY.get(normalize(raw)));
    }

    @JsonCreator
    public static ProgrammingLanguage parse(String raw) {
        return find(raw).orElseThrow(() ->
                new IllegalArgumentException("Unsupported language: " + raw));
    }

    private static Map<String, ProgrammingLanguage> index() {
        Map<String, ProgrammingLanguage> map = new HashMap<>();
        for (ProgrammingLanguage language : values()) {
            register(map, language.id, language);
            for (String alias : language.aliases) {
                register(map, alias, language);
            }
        }
        return Map.copyOf(map);
    }

    private static void register(Map<String, ProgrammingLanguage> map, String key, ProgrammingLanguage language) {
        String normalized = normalize(key);
        ProgrammingLanguage existing = map.putIfAbsent(normalized, language);
        if (existing != null && existing != language) {
            throw new IllegalStateException("Duplicate language key '" + normalized + "'");
        }
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
