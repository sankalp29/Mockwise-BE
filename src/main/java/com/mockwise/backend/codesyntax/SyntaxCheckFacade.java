package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.codesyntax.support.TempWorkspace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Application entry for syntax checks: resolve language, allocate workspace, run strategy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyntaxCheckFacade {

    private final LanguageToolchainRegistry registry;

    public SyntaxCheckResult check(String language, String code) {
        log.info("Syntax check requested for language: {}", language);
        Optional<LanguageToolchain> tool = registry.find(language);
        if (tool.isEmpty()) {
            return SyntaxCheckResult.unsupported(language == null ? "" : language);
        }
        try (TempWorkspace workspace = TempWorkspace.create("syntax_check_")) {
            return tool.get().checkSyntax(code == null ? "" : code, workspace.path());
        } catch (Exception e) {
            log.error("Syntax check failed for language {}", language, e);
            return SyntaxCheckResult.failed("Internal server error during syntax check: " + e.getMessage());
        }
    }
}
