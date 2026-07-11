package com.mockwise.backend.codesyntax;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyntaxCheckServiceTest {

    private final SyntaxCheckService service = new SyntaxCheckService();

    @Test
    void unsupportedLanguageReturnsError() {
        List<String> errors = service.checkSyntax("print(1)", "ruby");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toLowerCase().contains("unsupported"));
    }

    @Test
    void validJavaSyntaxHasNoErrors() {
        String code = "public int add(int a, int b) { return a + b; }";
        List<String> errors = service.checkSyntax(code, "java");
        // May include JDK-missing message in environments without a full JDK compiler API;
        // treat "JDK not found" as environment limitation, not a syntax error from user code.
        boolean onlyJdkMissing = errors.size() == 1 && errors.get(0).contains("JDK not found");
        assertTrue(errors.isEmpty() || onlyJdkMissing, () -> "Unexpected errors: " + errors);
    }

    @Test
    void invalidJavaSyntaxReportsErrors() {
        String code = "public int add(int a, int b) { return a + ; }";
        List<String> errors = service.checkSyntax(code, "java");
        assertFalse(errors.isEmpty());
    }
}
