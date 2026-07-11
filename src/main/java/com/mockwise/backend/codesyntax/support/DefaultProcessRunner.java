package com.mockwise.backend.codesyntax.support;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultProcessRunner implements ProcessRunner {

    @Override
    public ProcessResult run(List<String> command, Path workDir, Duration timeout) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cleaned = line.replace(workDir.toAbsolutePath() + workDir.getFileSystem().getSeparator(), "");
                // also strip classic File.separator form
                cleaned = cleaned.replace(workDir.toAbsolutePath() + java.io.File.separator, "");
                outputLines.add(cleaned);
            }
        }

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            outputLines.add("Command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command));
            return new ProcessResult(-1, outputLines, true);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0 && outputLines.isEmpty()) {
            outputLines.add("Command execution failed with exit code " + exitCode
                    + ". No specific error message provided by tool.");
        }
        return new ProcessResult(exitCode, outputLines, false);
    }
}
