package com.mockwise.backend.codesyntax.languages;

import com.mockwise.backend.codesyntax.LanguageToolchain;
import com.mockwise.backend.codesyntax.model.SyntaxCheckResult;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.FileWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class JavaToolchain implements LanguageToolchain {

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public Set<String> aliases() {
        return Set.of();
    }

    @Override
    public String displayName() {
        return "Java";
    }

    @Override
    public boolean isToolchainAvailable() {
        return javax.tools.ToolProvider.getSystemJavaCompiler() != null;
    }

    @Override
    public SyntaxCheckResult checkSyntax(String code, Path workDir) {
        List<String> errors = new ArrayList<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return SyntaxCheckResult.toolMissing(
                    "JDK not found. Please ensure a JDK is installed and JAVA_HOME is set correctly.");
        }

        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            Path sourceFile = workDir.resolve("Solution.java");

            String fullCode = code;
            if (!code.contains("class Solution")) {
                fullCode = "public class Solution {\n" + code + "\n}";
            }

            try (FileWriter writer = new FileWriter(sourceFile.toFile())) {
                writer.write(fullCode);
            }

            StandardJavaFileManager fileManager =
                    compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null);
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
            StringWriter output = new StringWriter();

            JavaCompiler.CompilationTask task = compiler.getTask(
                    output,
                    fileManager,
                    diagnostics,
                    Arrays.asList("-d", workDir.toAbsolutePath().toString()),
                    null,
                    compilationUnits);

            boolean success = task.call();
            if (!success) {
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    String message = diagnostic.getMessage(Locale.getDefault());
                    if (diagnostic.getSource() != null) {
                        message = message.replace(diagnostic.getSource().getName(), "Solution.java");
                    }
                    errors.add(String.format("Error on line %d: %s",
                            diagnostic.getLineNumber(),
                            message));
                }
            } else {
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    if (diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                        String message = diagnostic.getMessage(Locale.getDefault());
                        if (diagnostic.getSource() != null) {
                            message = message.replace(diagnostic.getSource().getName(), "Solution.java");
                        }
                        errors.add(String.format("Warning on line %d: %s",
                                diagnostic.getLineNumber(),
                                message));
                    }
                }
            }
            fileManager.close();
        } catch (Exception e) {
            return SyntaxCheckResult.failed("Internal server error during syntax check: " + e.getMessage());
        }

        return errors.isEmpty() ? SyntaxCheckResult.ok() : SyntaxCheckResult.syntaxErrors(errors);
    }
}
