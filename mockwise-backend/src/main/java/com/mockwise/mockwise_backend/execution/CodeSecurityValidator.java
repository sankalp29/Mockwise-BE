package com.mockwise.mockwise_backend.execution;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

public class CodeSecurityValidator {

    /**
     * Validates the given source code against the provided security rules.
     * Returns an Optional containing the violation message if a rule is violated,
     * or empty if the code passes all checks.
     */
    public Optional<String> validate(String code, List<SecurityRule> rules) {
        for (SecurityRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(code);
            if (matcher.find()) {
                return Optional.of("Security violation: " + rule.description()
                        + " (matched: \"" + matcher.group() + "\")");
            }
        }
        return Optional.empty();
    }
}