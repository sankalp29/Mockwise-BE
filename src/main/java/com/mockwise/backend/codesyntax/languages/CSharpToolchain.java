package com.mockwise.backend.codesyntax.languages;

import com.mockwise.backend.codesyntax.LanguageToolchain;
import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import com.mockwise.backend.codesyntax.support.CommandProbe;
import com.mockwise.backend.codesyntax.support.ProcessResult;
import com.mockwise.backend.codesyntax.support.ProcessRunner;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CSharpToolchain implements LanguageToolchain {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private final ProcessRunner processRunner;

    public CSharpToolchain(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public String languageId() {
        return "csharp";
    }

    @Override
    public String displayName() {
        return "C#";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("cs", "c#");
    }

    @Override
    public boolean isToolchainAvailable() {
        return CommandProbe.exists(processRunner, "dotnet", "--version");
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        try {
            // Minimal SDK-style project for a single-file compile check
            String csproj = """
                    <Project Sdk="Microsoft.NET.Sdk">
                      <PropertyGroup>
                        <OutputType>Exe</OutputType>
                        <TargetFramework>net8.0</TargetFramework>
                        <ImplicitUsings>enable</ImplicitUsings>
                        <Nullable>enable</Nullable>
                      </PropertyGroup>
                    </Project>
                    """;
            Files.writeString(workDir.resolve("Solution.csproj"), csproj);

            String source = code;
            if (!code.contains("class ") && !code.contains("top-level") && !code.contains("Main(")) {
                // Allow snippet-style solutions
                source = "public class Program {\n  public static void Main() {\n" + code + "\n  }\n}\n";
            }
            Files.writeString(workDir.resolve("Program.cs"), source);
        } catch (IOException e) {
            return SyntaxCheckResult.failed("Failed to write C# project: " + e.getMessage());
        }

        try {
            ProcessResult result = processRunner.run(
                    List.of("dotnet", "build", "--nologo", "-v", "q"),
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
                    .filter(line -> line.contains("error") || line.contains("Error") || line.contains(": error"))
                    .collect(Collectors.toList());
            if (errors.isEmpty()) {
                errors = result.outputLines().stream()
                        .filter(line -> !line.trim().isEmpty())
                        .collect(Collectors.toList());
            }
            return errors.isEmpty()
                    ? SyntaxCheckResult.syntaxErrors(List.of("C# syntax check failed"))
                    : SyntaxCheckResult.syntaxErrors(errors);
        } catch (IOException e) {
            return SyntaxCheckResult.toolMissing(".NET SDK (dotnet) not found. Install .NET to check C# syntax.");
        } catch (Exception e) {
            return SyntaxCheckResult.failed("C# syntax check failed: " + e.getMessage());
        }
    }
}
