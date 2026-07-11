package com.mockwise.backend.codesyntax;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LanguageToolchainRegistryTest {

    private final LanguageToolchainRegistry registry = ToolchainTestFactory.registry();

    @Test
    void resolvesCanonicalLanguageIds() {
        assertEquals("java", registry.require("java").languageId());
        assertEquals("python", registry.require("python").languageId());
        assertEquals("cpp", registry.require("cpp").languageId());
        assertEquals("javascript", registry.require("javascript").languageId());
        assertEquals("typescript", registry.require("typescript").languageId());
        assertEquals("go", registry.require("go").languageId());
        assertEquals("rust", registry.require("rust").languageId());
        assertEquals("ruby", registry.require("ruby").languageId());
        assertEquals("scala", registry.require("scala").languageId());
        assertEquals("csharp", registry.require("csharp").languageId());
    }

    @Test
    void resolvesAliasesCaseInsensitive() {
        assertEquals("python", registry.require("PY").languageId());
        assertEquals("python", registry.require("python3").languageId());
        assertEquals("cpp", registry.require("C++").languageId());
        assertEquals("java", registry.require("Java").languageId());
        assertEquals("javascript", registry.require("js").languageId());
        assertEquals("typescript", registry.require("TS").languageId());
        assertEquals("go", registry.require("golang").languageId());
        assertEquals("rust", registry.require("rs").languageId());
        assertEquals("ruby", registry.require("rb").languageId());
        assertEquals("csharp", registry.require("c#").languageId());
        assertEquals("csharp", registry.require("cs").languageId());
    }

    @Test
    void unknownLanguageIsEmpty() {
        assertTrue(registry.find("cobol").isEmpty());
        assertTrue(registry.find("").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void supportedLanguageIdsContainsAllBuiltIns() {
        Set<String> ids = registry.supportedLanguageIds();
        assertTrue(ids.containsAll(Set.of(
                "java", "python", "cpp", "javascript", "typescript",
                "go", "rust", "ruby", "scala", "csharp")));
        assertEquals(10, ids.size());
    }
}
