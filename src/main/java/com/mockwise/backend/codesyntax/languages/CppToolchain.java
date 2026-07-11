package com.mockwise.backend.codesyntax.languages;

import com.mockwise.backend.codesyntax.LanguageToolchain;
import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.codesyntax.support.ProcessResult;
import com.mockwise.backend.codesyntax.support.CommandProbe;
import com.mockwise.backend.codesyntax.support.ProcessRunner;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CppToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final ProcessRunner processRunner;

    public CppToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "cpp";
    }


    @Override
    public String displayName() {
        return "C++";
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "g++", "--version");
    }
    @Override
    public Set<String> aliases() {
        return Set.of("c++", "cplusplus");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        String processedCode = code;
        Pattern bitsPattern = Pattern.compile(
                "#\\s*include\\s*[<\"]\\s*bits\\s*/\\s*stdc\\s*\\+\\+\\s*\\.\\s*h\\s*[>\"]",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        Matcher matcher = bitsPattern.matcher(code);
        if (matcher.find()) {
            String standardHeaders =
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
                    + "#include <functional>\n";
            processedCode = matcher.replaceAll(standardHeaders);
        }

        Path sourceFile = workDir.resolve("solution.cpp");
        try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
            writer.write(processedCode);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write C++ source: " + e.getMessage());
        }

        try {
            ProcessResult result = processRunner.run(
                    List.of("g++", "-std=c++17", "-fsyntax-only", sourceFile.getFileName().toString()),
                    workDir,
                    TIMEOUT);
            if (result.timedOut()) {
                return SyntaxCheckResult.failed(String.join("\n", result.outputLines()));
            }
            List<String> errors = result.outputLines().stream()
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());
            if (result.exitCode() != 0 && errors.isEmpty()) {
                return SyntaxCheckResult.toolMissing(
                        "g++ failed with exit code " + result.exitCode()
                                + ". Ensure g++ is installed.");
            }
            return errors.isEmpty() ? SyntaxCheckResult.ok() : SyntaxCheckResult.syntaxErrors(errors);
        } catch (IOException e) {
            return SyntaxCheckResult.toolMissing(
                    "g++ not found. Please ensure a C++ compiler is installed.");
        } catch (Exception e) {
            return SyntaxCheckResult.failed("C++ syntax check failed: " + e.getMessage());
        }
    }
}
