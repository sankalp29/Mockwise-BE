package com.mockwise.backend.codesyntax;

import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backward-compatible entry used by {@code InterviewController}.
 * Delegates to {@link SyntaxCheckFacade} (Strategy + Registry).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyntaxCheckService {

    private final SyntaxCheckFacade facade;

    public List<String> checkSyntax(String code, String language) {
        SyntaxCheckResult result = facade.check(language, code);
        return result.messages();
    }
}
