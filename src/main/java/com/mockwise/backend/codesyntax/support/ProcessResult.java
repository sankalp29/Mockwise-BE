package com.mockwise.backend.codesyntax.support;

import java.util.List;

public record ProcessResult(int exitCode, List<String> outputLines, boolean timedOut) {
    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }
}
