package com.mockwise.backend.codesyntax.languages;

import com.mockwise.backend.codesyntax.LanguageToolchain;
import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.codesyntax.support.CommandProbe;
import com.mockwise.backend.codesyntax.support.ProcessResult;
import com.mockwise.backend.codesyntax.support.ProcessRunner;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GoToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final ProcessRunner processRunner;

    public GoToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "go";
    }

    @Override
    public String displayName() {
        return "Go";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("golang");
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "go", "version");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        String source = code;
        if (!code.contains("package ")) {
            source = "package main\n\n" + code;
        }
        Path sourceFile = workDir.resolve("solution.go");
        try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
            writer.write(source);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write Go source: " + e.getMessage());
        }
        try {
            // Compile only (no link) — syntax/type check without producing a binary
            ProcessResult result = processRunner.run(
                    List.of("go", "tool", "compile", "-o", "solution.o", sourceFile.getFileName().toString()),
                    workDir,
                    TIMEOUT);
            if (result.timedOut()) {
                return SyntaxCheckResult.failed(String.join("\n", result.outputLines()));
            }
            if (result.isSuccess()) {
                return SyntaxCheckResult.ok();
            }
            List<String> errors = result.outputLines().stream()
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());
            return errors.isEmpty()
                    ? SyntaxCheckResult.syntaxErrors(List.of("Go syntax check failed"))
                    : SyntaxCheckResult.syntaxErrors(errors);
        } catch (IOException e) {
            return SyntaxCheckResult.toolMissing("Go toolchain not found. Install Go to check Go syntax.");
        } catch (Exception e) {
            return SyntaxCheckResult.failed("Go syntax check failed: " + e.getMessage());
        }
    }
}
