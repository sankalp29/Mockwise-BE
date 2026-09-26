package com.mockwise.backend.service.codesyntax.languages;

import com.mockwise.backend.repository.question.ProgrammingLanguage;
import com.mockwise.backend.service.codesyntax.LanguageToolchain;
import com.mockwise.backend.service.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.service.codesyntax.support.CommandProbe;
import com.mockwise.backend.service.codesyntax.support.ProcessResult;
import com.mockwise.backend.service.codesyntax.support.ProcessRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CppToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Pattern BITS_HEADER = Pattern.compile(
            "#\\s*include\\s*[<\"]\\s*bits\\s*/\\s*stdc\\s*\\+\\+\\s*\\.\\s*h\\s*[\">]",
            Pattern.CASE_INSENSITIVE);

    private static final String BITS_CONTENT =
            "#include <iostream>\n"
            + "#include <vector>\n"
            + "#include <string>\n"
            + "#include <algorithm>\n"
            + "#include <map>\n"
            + "#include <set>\n"
            + "#include <unordered_map>\n"
            + "#include <unordered_set>\n"
            + "#include <queue>\n"
            + "#include <stack>\n"
            + "#include <deque>\n"
            + "#include <climits>\n"
            + "#include <cmath>\n"
            + "#include <numeric>\n"
            + "#include <utility>\n"
            + "#include <functional>\n"
            + "#include <random>\n";

    private final ProcessRunner processRunner;

    public CppToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.CPP;
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "g++", "--version");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        // Keep the editor buffer unchanged so g++ line numbers match Monaco.
        // Provide bits/stdc++.h as a real header under the temp workdir instead of
        // expanding it inline (which previously shifted every line after the include).
        String source = code == null ? "" : code;
        if (BITS_HEADER.matcher(source).find()) {
            try {
                Path bitsDir = workDir.resolve("bits");
                Files.createDirectories(bitsDir);
                Files.writeString(bitsDir.resolve("stdc++.h"), BITS_CONTENT);
            } catch (IOException e) {
                return SyntaxCheckResult.failed("Failed to prepare C++ headers: " + e.getMessage());
            }
        }

        Path sourceFile = workDir.resolve("solution.cpp");
        try {
            Files.writeString(sourceFile, source);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write C++ source: " + e.getMessage());
        }

        try {
            ProcessResult result = processRunner.run(
                    List.of(
                            "g++",
                            "-std=c++17",
                            "-fsyntax-only",
                            "-I" + workDir.toAbsolutePath(),
                            sourceFile.getFileName().toString()),
                    workDir,
                    TIMEOUT);
            if (result.timedOut()) {
                return SyntaxCheckResult.failed(String.join("\n", result.outputLines()));
            }
            List<String> errors = result.outputLines().stream()
                    .filter(line -> !line.trim().isEmpty())
                    .map(line -> line.replace(workDir.toAbsolutePath() + "/", ""))
                    .map(line -> line.replace(workDir.toAbsolutePath() + java.io.File.separator, ""))
                    .collect(Collectors.toList());
            if (result.exitCode() != 0 && errors.isEmpty()) {
                return SyntaxCheckResult.toolMissing(
                        "g++ failed with exit code " + result.exitCode()
                                + ". Ensure g++ is installed.");
            }
            // Non-zero only => errors; ignore notes when exit is 0
            if (result.exitCode() == 0) {
                return SyntaxCheckResult.ok();
            }
            return errors.isEmpty()
                    ? SyntaxCheckResult.syntaxErrors(List.of("C++ syntax check failed"))
                    : SyntaxCheckResult.syntaxErrors(errors);
        } catch (IOException e) {
            return SyntaxCheckResult.toolMissing(
                    "g++ not found. Please ensure a C++ compiler is installed.");
        } catch (Exception e) {
            return SyntaxCheckResult.failed("C++ syntax check failed: " + e.getMessage());
        }
    }
}
