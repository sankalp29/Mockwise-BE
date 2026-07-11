package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SupportedLanguage;
import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/codesyntax")
@RequiredArgsConstructor
public class CodeSyntaxController {

    private final LanguageSupportService languageSupportService;
    private final SyntaxCheckFacade syntaxCheckFacade;

    @GetMapping("/languages")
    public ResponseEntity<List<SupportedLanguage>> languages() {
        return ResponseEntity.ok(languageSupportService.listSupportedLanguages());
    }

    /**
     * Structured syntax check (richer than interview endpoint). Still requires auth via security config.
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestBody Map<String, String> body) {
        String language = body.getOrDefault("language", "");
        String code = body.getOrDefault("code", "");
        SyntaxCheckResult result = syntaxCheckFacade.check(language, code);
        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "errors", result.messages(),
                "toolStatus", result.toolStatus().name()
        ));
    }
}
