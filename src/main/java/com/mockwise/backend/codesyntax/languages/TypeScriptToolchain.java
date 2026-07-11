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
public class TypeScriptToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final ProcessRunner processRunner;

    public TypeScriptToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "typescript";
    }

    @Override
    public String displayName() {
        return "TypeScript";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("ts");
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "tsc", "--version")
                || CommandProbe.exists(processRunner, "npx", "--version");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        Path sourceFile = workDir.resolve("solution.ts");
        try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
            writer.write(code);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write TypeScript source: " + e.getMessage());
        }

        // Prefer global tsc; fall back to npx typescript
        List<List<String>> candidates = List.of(
                List.of("tsc", "--noEmit", "--pretty", "false", "--target", "ES2020",
                        sourceFile.getFileName().toString()),
                List.of("npx", "--yes", "typescript", "tsc", "--noEmit", "--pretty", "false",
                        "--target", "ES2020", sourceFile.getFileName().toString())
        );

        Exception last = null;
        for (List<String> cmd : candidates) {
            try {
                ProcessResult result = processRunner.run(cmd, workDir, TIMEOUT);
                if (result.timedOut()) {
                    return SyntaxCheckResult.failed(String.join("\n", result.outputLines()));
                }
                if (result.isSuccess()) {
                    return SyntaxCheckResult.ok();
                }
                List<String> errors = result.outputLines().stream()
                        .filter(line -> !line.trim().isEmpty())
                        .collect(Collectors.toList());
                // If tsc missing, npx may still work; if both fail with not found, outer catch
                if (!errors.isEmpty() && !looksLikeMissingBinary(errors)) {
                    return SyntaxCheckResult.syntaxErrors(errors);
                }
            } catch (IOException e) {
                last = e;
            } catch (Exception e) {
                return SyntaxCheckResult.failed("TypeScript syntax check failed: " + e.getMessage());
            }
        }
        return SyntaxCheckResult.toolMissing(
                "TypeScript compiler (tsc) not found. Install typescript or ensure npx is available."
                        + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    private static boolean looksLikeMissingBinary(List<String> lines) {
        String joined = String.join(" ", lines).toLowerCase();
        return joined.contains("not found") || joined.contains("no such file");
    }
}
