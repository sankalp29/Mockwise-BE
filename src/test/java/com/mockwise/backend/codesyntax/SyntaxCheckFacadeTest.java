package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.codesyntax.model.ToolStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyntaxCheckFacadeTest {

    private final SyntaxCheckFacade facade = ToolchainTestFactory.facade();

    @Test
    void unsupportedLanguageReturnsStructuredError() {
        SyntaxCheckResult result = facade.check("cobol", "puts 1");
        assertFalse(result.success());
        assertEquals(ToolStatus.MISSING, result.toolStatus());
        assertFalse(result.messages().isEmpty());
        assertTrue(result.messages().get(0).toLowerCase().contains("unsupported"));
    }

    @Test
    void validJavaSyntaxHasNoErrorsOrOnlyMissingJdk() {
        SyntaxCheckResult result = facade.check("java", "public int add(int a, int b) { return a + b; }");
        boolean onlyJdkMissing = result.toolStatus() == ToolStatus.MISSING
                && result.messages().size() == 1
                && result.messages().get(0).contains("JDK not found");
        assertTrue(result.success() || onlyJdkMissing, () -> "Unexpected: " + result);
    }

    @Test
    void invalidJavaSyntaxReportsErrors() {
        SyntaxCheckResult result = facade.check("java", "public int add(int a, int b) { return a + ; }");
        if (result.toolStatus() == ToolStatus.MISSING) {
            // Environment without JDK — cannot assert syntax diagnostics
            assertTrue(result.messages().get(0).contains("JDK not found"));
            return;
        }
        assertFalse(result.success());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void nullCodeDoesNotThrow() {
        SyntaxCheckResult result = facade.check("java", null);
        assertNotNull(result);
        assertNotNull(result.messages());
    }
}
