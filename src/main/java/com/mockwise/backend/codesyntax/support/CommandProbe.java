package com.mockwise.backend.codesyntax.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Utility to detect whether a CLI tool exists on PATH. */
public final class CommandProbe {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private CommandProbe() {}

    public static boolean exists(ProcessRunner runner, String... commandAndArgs) {
        if (commandAndArgs == null || commandAndArgs.length == 0) {
            return false;
        }
        try {
            Path cwd = Path.of(System.getProperty("java.io.tmpdir"));
            ProcessResult result = runner.run(List.of(commandAndArgs), cwd, PROBE_TIMEOUT);
            // Binary found if process started (even non-zero exit). timedOut alone doesn't mean missing.
            return !result.timedOut() || !result.outputLines().isEmpty();
        } catch (Exception e) {
            // ProcessBuilder throws IOException when executable not found
            return false;
        }
    }
}
