package com.mockwise.backend.service.codesyntax.languages;

import com.mockwise.backend.service.codesyntax.LanguageToolchain;
import com.mockwise.backend.service.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.service.codesyntax.support.ProcessResult;
import com.mockwise.backend.service.codesyntax.support.CommandProbe;
import com.mockwise.backend.service.codesyntax.support.ProcessRunner;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PythonToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private final ProcessRunner processRunner;

    public PythonToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "python";
    }


    @Override
    public String displayName() {
        return "Python";
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "python3", "--version")
                || CommandProbe.exists(processRunner, "python", "--version");
    }
    @Override
    public Set<String> aliases() {
        return Set.of("py", "python3");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        Path sourceFile = workDir.resolve("solution.py");
        try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
            writer.write(code);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write Python source: " + e.getMessage());
        }

        List<String> pythonCommands = Arrays.asList("python3", "python", "py");
        for (String pythonCmd : pythonCommands) {
            try {
                ProcessResult result = processRunner.run(
                        List.of(pythonCmd, "-m", "py_compile", sourceFile.getFileName().toString()),
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
                        .map(line -> line.replace(workDir.toAbsolutePath() + java.io.File.separator, ""))
                        .map(line -> line.replace(workDir.toAbsolutePath() + "/", ""))
                        .collect(Collectors.toList());
                return errors.isEmpty()
                        ? SyntaxCheckResult.syntaxErrors(List.of("Python syntax check failed"))
                        : SyntaxCheckResult.syntaxErrors(errors);
            } catch (IOException e) {
                // try next interpreter name
            } catch (Exception e) {
                return SyntaxCheckResult.failed("Python syntax check failed: " + e.getMessage());
            }
        }
        return SyntaxCheckResult.toolMissing(
                "Python interpreter not found. Please ensure Python 3 is installed.");
    }
}
