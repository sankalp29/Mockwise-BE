package com.mockwise.backend.repository.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammingLanguageTest {

    @Test
    void parsesCanonicalIdsAndAliases() {
        assertEquals(ProgrammingLanguage.JAVA, ProgrammingLanguage.parse("java"));
        assertEquals(ProgrammingLanguage.JAVA, ProgrammingLanguage.parse(" Java "));
        assertEquals(ProgrammingLanguage.CPP, ProgrammingLanguage.parse("C++"));
        assertEquals(ProgrammingLanguage.JAVASCRIPT, ProgrammingLanguage.parse("js"));
        assertEquals(ProgrammingLanguage.CSHARP, ProgrammingLanguage.parse("c#"));
        assertEquals(ProgrammingLanguage.PYTHON, ProgrammingLanguage.parse("python3"));
    }

    @Test
    void rejectsAnUnknownLanguage() {
        assertTrue(ProgrammingLanguage.find("brainfuck").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ProgrammingLanguage.parse("brainfuck"));
    }

    @Test
    void idIsTheStoredValue() {
        assertEquals("csharp", ProgrammingLanguage.CSHARP.id());
        assertEquals("C#", ProgrammingLanguage.CSHARP.displayName());
    }
}
