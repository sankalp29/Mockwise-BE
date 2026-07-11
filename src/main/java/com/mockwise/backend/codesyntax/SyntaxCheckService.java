package com.mockwise.backend.codesyntax;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SyntaxCheckService {

    public List<String> checkSyntax(String code, String language) {
        log.info("Performing syntax check for language: {}", language);
        List<String> errors = new ArrayList<>();
        File tempDir = null;

        try {
            tempDir = Files.createTempDirectory("syntax_check").toFile();

            switch (language.toLowerCase()) {
                case "java":
                    return checkJavaSyntax(code, tempDir);
                case "python":
                    return checkPythonSyntax(code, tempDir);
                case "cpp":
                    return checkCppSyntax(code, tempDir);
                default:
                    errors.add("Unsupported language for syntax checking: " + language);
                    return errors;
            }
        } catch (Exception e) {
            log.error("Exception during syntax check for language {}", language, e);
            errors.add("Internal server error during syntax check: " + e.getMessage());
            return errors;
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir.toPath())
                            .sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (Exception e) {
                    log.warn("Failed to clean up temporary directory: {}", tempDir.getAbsolutePath(), e);
                }
            }
        }
    }

    private List<String> checkJavaSyntax(String code, File tempDir) throws Exception {
        List<String> errors = new ArrayList<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            errors.add("JDK not found. Please ensure a JDK is installed and JAVA_HOME is set correctly.");
            return errors;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        File sourceFile = new File(tempDir, "Solution.java");

        String fullCode = code;
        if (!code.contains("class Solution")) {
            fullCode = "public class Solution {\n" + code + "\n}";
        }

        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(fullCode);
        }

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null);
        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
        StringWriter output = new StringWriter();

        JavaCompiler.CompilationTask task = compiler.getTask(
                output,
                fileManager,
                diagnostics,
                Arrays.asList("-d", tempDir.getAbsolutePath()),
                null,
                compilationUnits);

        boolean success = task.call();

        if (!success) {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                String message = diagnostic.getMessage(Locale.getDefault());
                message = message.replace(diagnostic.getSource().getName(), "Solution.java");
                errors.add(String.format("Error on line %d: %s",
                        diagnostic.getLineNumber(),
                        message));
            }
        } else {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                    String message = diagnostic.getMessage(Locale.getDefault());
                    message = message.replace(diagnostic.getSource().getName(), "Solution.java");
                    errors.add(String.format("Warning on line %d: %s",
                            diagnostic.getLineNumber(),
                            message));
                }
            }
        }
        fileManager.close();
        return errors;
    }

    private List<String> executeCommand(List<String> command, File workingDir, String fileName) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        List<String> outputLines = new ArrayList<>();
        try (java.io.BufferedReader reader =
                     new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line.replace(workingDir.getAbsolutePath() + File.separator, ""));
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && outputLines.isEmpty()) {
            outputLines.add("Command execution failed with exit code " + exitCode
                    + ". No specific error message provided by tool.");
        }
        return outputLines;
    }

    private List<String> checkPythonSyntax(String code, File tempDir) throws Exception {
        File sourceFile = new File(tempDir, "solution.py");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(code);
        }

        List<String> pythonCommands = Arrays.asList("python3", "python", "py");

        for (String pythonCmd : pythonCommands) {
            try {
                List<String> command = Arrays.asList(pythonCmd, "-m", "py_compile", sourceFile.getName());
                List<String> output = executeCommand(command, tempDir, sourceFile.getName());

                if (output.isEmpty()) {
                    return List.of();
                }
                return output.stream()
                        .filter(line -> line.contains("SyntaxError")
                                || line.contains("IndentationError")
                                || line.contains("Error")
                                || line.contains("line"))
                        .map(line -> line.replace(tempDir.getAbsolutePath() + File.separator, ""))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                continue;
            }
        }

        throw new Exception("Python interpreter not found. Please ensure Python 3 is installed.");
    }

    private List<String> checkCppSyntax(String code, File tempDir) throws Exception {
        String processedCode = code;

        Pattern bitsPattern = Pattern.compile(
                "#\\s*include\\s*[<\"]\\s*bits\\s*/\\s*stdc\\s*\\+\\+\\s*\\.\\s*h\\s*[>\"]",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        Matcher matcher = bitsPattern.matcher(code);

        if (matcher.find()) {
            log.info("User code uses bits/stdc++.h, replacing with standard headers");
            String standardHeaders =
                    "#include <iostream>\n" +
                    "#include <vector>\n" +
                    "#include <string>\n" +
                    "#include <algorithm>\n" +
                    "#include <map>\n" +
                    "#include <set>\n" +
                    "#include <unordered_map>\n" +
                    "#include <unordered_set>\n" +
                    "#include <queue>\n" +
                    "#include <stack>\n" +
                    "#include <deque>\n" +
                    "#include <climits>\n" +
                    "#include <cmath>\n" +
                    "#include <numeric>\n" +
                    "#include <utility>\n" +
                    "#include <functional>\n";
            processedCode = matcher.replaceAll(standardHeaders);
        }

        File sourceFile = new File(tempDir, "solution.cpp");
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write(processedCode);
        }

        List<String> command = Arrays.asList(
                "g++",
                "-std=c++17",
                "-fsyntax-only",
                sourceFile.getName()
        );

        List<String> output = executeCommand(command, tempDir, sourceFile.getName());
        return output.stream()
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.toList());
    }
}
