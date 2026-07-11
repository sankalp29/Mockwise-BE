package com.mockwise.backend.codesyntax.languages;

import com.mockwise.backend.codesyntax.SyntaxCheckFacade;
import com.mockwise.backend.codesyntax.ToolchainTestFactory;
import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.codesyntax.model.ToolStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior tests for new languages. If the local tool is not installed,
 * ToolStatus.MISSING is acceptable; "unsupported" is not.
 */
class NewLanguageToolchainsTest {

    private final SyntaxCheckFacade facade = ToolchainTestFactory.facade();

    @ParameterizedTest
    @CsvSource({
            "javascript, const x = 1;",
            "js, const x = 1;",
            "typescript, const x: number = 1;",
            "go, func main() {}",
            "rust, fn main() {}",
            "ruby, puts 1",
            "scala, object Main",
            "csharp, System.Console.WriteLine(1);"
    })
    void registeredLanguageDoesNotReportUnsupported(String language, String code) {
        SyntaxCheckResult result = facade.check(language, code);
        String joined = String.join(" ", result.messages()).toLowerCase();
        assertFalse(joined.contains("unsupported language"),
                () -> language + " should be registered, got: " + result.messages());
        assertNotNull(result.toolStatus());
    }

    @Test
    void invalidJavascriptReportsErrorOrMissingNode() {
        SyntaxCheckResult result = facade.check("javascript", "const x = ;");
        if (result.toolStatus() == ToolStatus.MISSING) {
            assertTrue(result.messages().get(0).toLowerCase().contains("node"));
            return;
        }
        assertFalse(result.success());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void invalidRubyReportsErrorOrMissingRuby() {
        SyntaxCheckResult result = facade.check("ruby", "def foo(");
        if (result.toolStatus() == ToolStatus.MISSING) {
            assertTrue(result.messages().get(0).toLowerCase().contains("ruby"));
            return;
        }
        assertFalse(result.success());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void languageSupportListsTenLanguages() {
        var list = ToolchainTestFactory.languageSupportService().listSupportedLanguages();
        assertEquals(10, list.size());
        assertTrue(list.stream().anyMatch(l -> l.id().equals("rust")));
        assertTrue(list.stream().anyMatch(l -> l.id().equals("javascript")));
    }
}
