package com.mockwise.backend.service.codesyntax.languages;

import com.mockwise.backend.service.codesyntax.LanguageToolchain;
import com.mockwise.backend.service.codesyntax.model.SyntaxCheckResult;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaToolchain implements LanguageToolchain {

    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("public\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ANY_CLASS =
            Pattern.compile("\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)");

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
        return ToolProvider.getSystemJavaCompiler() != null;
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

            // Prefer compiling the editor buffer as-is so diagnostic lines match Monaco.
            String fullCode = code == null ? "" : code;
            int lineOffset = 0;
            String displayName = "Solution.java";

            Matcher publicClass = PUBLIC_CLASS.matcher(fullCode);
            Matcher anyClass = ANY_CLASS.matcher(fullCode);
            if (publicClass.find()) {
                displayName = publicClass.group(1) + ".java";
            } else if (anyClass.find()) {
                displayName = anyClass.group(1) + ".java";
            } else {
                // Snippet-only: wrap once and map compiler lines back to the buffer.
                fullCode = "public class Solution {\n" + fullCode + "\n}\n";
                lineOffset = 1;
                displayName = "Solution.java";
            }

            Path sourceFile = workDir.resolve(displayName);
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

            boolean success = Boolean.TRUE.equals(task.call());
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                Diagnostic.Kind kind = diagnostic.getKind();
                boolean warning = kind == Diagnostic.Kind.WARNING
                        || kind == Diagnostic.Kind.MANDATORY_WARNING
                        || kind == Diagnostic.Kind.NOTE;
                if (warning) {
                    continue;
                }
                errors.add(formatDiagnostic(diagnostic, displayName, lineOffset, false));
            }
            if (!success && errors.isEmpty()) {
                String dumped = output.toString().trim();
                errors.add(dumped.isEmpty() ? "Compilation failed." : dumped);
            }
            fileManager.close();
        } catch (Exception e) {
            return SyntaxCheckResult.failed("Internal server error during syntax check: " + e.getMessage());
        }

        return errors.isEmpty() ? SyntaxCheckResult.ok() : SyntaxCheckResult.syntaxErrors(errors);
    }

    private static String formatDiagnostic(
            Diagnostic<? extends JavaFileObject> diagnostic,
            String displayName,
            int lineOffset,
            boolean warning) {
        String message = diagnostic.getMessage(Locale.getDefault());
        if (diagnostic.getSource() != null) {
            message = message.replace(diagnostic.getSource().getName(), displayName);
        }
        long rawLine = diagnostic.getLineNumber();
        long userLine = rawLine > 0 ? Math.max(1, rawLine - lineOffset) : rawLine;
        long col = diagnostic.getColumnNumber() > 0 ? diagnostic.getColumnNumber() : 1;
        String kind = warning ? "Warning" : "Error";
        if (userLine > 0) {
            return String.format("%s on line %d:%d: %s", kind, userLine, col, message);
        }
        return String.format("%s: %s", kind, message);
    }
}
