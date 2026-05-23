package com.mockwise.mockwise_backend.execution;

import java.util.regex.Pattern;

public record SecurityRule(Pattern pattern, String description) {
    public SecurityRule(String regex, String description) {
        this(Pattern.compile(regex, Pattern.MULTILINE), description);
    }
}