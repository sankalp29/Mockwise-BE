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
public class RubyToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final ProcessRunner processRunner;

    public RubyToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "ruby";
    }

    @Override
    public String displayName() {
        return "Ruby";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("rb");
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "ruby", "--version");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        Path sourceFile = workDir.resolve("solution.rb");
        try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
            writer.write(code);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write Ruby source: " + e.getMessage());
        }
        try {
            ProcessResult result = processRunner.run(
                    List.of("ruby", "-c", sourceFile.getFileName().toString()),
                    workDir,
                    TIMEOUT);
            if (result.timedOut()) {
                return SyntaxCheckResult.failed(String.join("\n", result.outputLines()));
            }
            // ruby -c prints "Syntax OK" on success
            if (result.isSuccess()) {
                return SyntaxCheckResult.ok();
            }
            List<String> errors = result.outputLines().stream()
                    .filter(line -> !line.trim().isEmpty())
                    .filter(line -> !line.equalsIgnoreCase("Syntax OK"))
                    .collect(Collectors.toList());
            return errors.isEmpty()
                    ? SyntaxCheckResult.syntaxErrors(List.of("Ruby syntax check failed"))
                    : SyntaxCheckResult.syntaxErrors(errors);
        } catch (IOException e) {
            return SyntaxCheckResult.toolMissing("Ruby not found. Install Ruby to check Ruby syntax.");
        } catch (Exception e) {
            return SyntaxCheckResult.failed("Ruby syntax check failed: " + e.getMessage());
        }
    }
}
