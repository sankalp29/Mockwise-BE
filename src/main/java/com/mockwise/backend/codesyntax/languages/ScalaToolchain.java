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
public class ScalaToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private final ProcessRunner processRunner;

    public ScalaToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "scala";
    }

    @Override
    public String displayName() {
        return "Scala";
    }

    @Override
    public Set<String> aliases() {
        return Set.of();
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "scalac", "-version")
                || CommandProbe.exists(processRunner, "scala-cli", "version");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        Path sourceFile = workDir.resolve("Solution.scala");
        try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
            writer.write(code);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write Scala source: " + e.getMessage());
        }

        List<List<String>> candidates = List.of(
                List.of("scalac", "-Xstop-after:parser", sourceFile.getFileName().toString()),
                List.of("scala-cli", "compile", "--server=false", sourceFile.getFileName().toString())
        );

        Exception lastMissing = null;
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
                if (!errors.isEmpty()) {
                    return SyntaxCheckResult.syntaxErrors(errors);
                }
            } catch (IOException e) {
                lastMissing = e;
            } catch (Exception e) {
                return SyntaxCheckResult.failed("Scala syntax check failed: " + e.getMessage());
            }
        }
        return SyntaxCheckResult.toolMissing(
                "scalac/scala-cli not found. Install Scala to check Scala syntax."
                        + (lastMissing != null ? " (" + lastMissing.getMessage() + ")" : ""));
    }
}
