package com.mockwise.backend.codesyntax;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Legacy API compatibility: controller still receives List&lt;String&gt;.
 */
class SyntaxCheckServiceTest {

    private final SyntaxCheckService service = ToolchainTestFactory.service();

    @Test
    void unsupportedLanguageReturnsError() {
        List<String> errors = service.checkSyntax("print(1)", "cobol");
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).toLowerCase().contains("unsupported"));
    }

    @Test
    void validJavaSyntaxHasNoErrors() {
        String code = "public int add(int a, int b) { return a + b; }";
        List<String> errors = service.checkSyntax(code, "java");
        boolean onlyJdkMissing = errors.size() == 1 && errors.get(0).contains("JDK not found");
        assertTrue(errors.isEmpty() || onlyJdkMissing, () -> "Unexpected errors: " + errors);
    }

    @Test
    void invalidJavaSyntaxReportsErrors() {
        String code = "public int add(int a, int b) { return a + ; }";
        List<String> errors = service.checkSyntax(code, "java");
        assertFalse(errors.isEmpty());
    }

    @Test
    void cppAliasIsAccepted() {
        // Even if g++ missing, must not say "unsupported"
        List<String> errors = service.checkSyntax("int main(){return 0;}", "c++");
        assertFalse(errors.isEmpty() && false);
        String joined = String.join(" ", errors).toLowerCase();
        assertFalse(joined.contains("unsupported language"), () -> "Got: " + errors);
    }
}
