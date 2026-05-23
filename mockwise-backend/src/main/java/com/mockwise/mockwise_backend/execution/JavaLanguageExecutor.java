package com.mockwise.mockwise_backend.execution;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaLanguageExecutor implements LanguageExecutor {

    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("public\\s+class\\s+(\\w+)");

    private final List<SecurityRule> securityRules = List.of(
            new SecurityRule("Runtime\\s*\\.\\s*getRuntime", "Runtime access is not allowed"),
            new SecurityRule("ProcessBuilder", "Process execution is not allowed"),
            new SecurityRule("new\\s+File\\s*\\(", "File system access is not allowed"),
            new SecurityRule("FileWriter|FileReader|FileInputStream|FileOutputStream|RandomAccessFile",
                    "File I/O is not allowed"),
            new SecurityRule("java\\.nio\\.file", "File system access via NIO is not allowed"),
            new SecurityRule("java\\.net\\.", "Network access is not allowed"),
            new SecurityRule("Socket|ServerSocket|DatagramSocket",
                    "Network socket access is not allowed"),
            new SecurityRule("HttpURLConnection|HttpClient", "HTTP connections are not allowed"),
            new SecurityRule("new\\s+URL\\s*\\(", "URL access is not allowed"),
            new SecurityRule("System\\s*\\.\\s*exit", "System.exit is not allowed"),
            new SecurityRule("java\\.lang\\.reflect\\.", "Reflection is not allowed"),
            new SecurityRule("Class\\.forName", "Dynamic class loading is not allowed"),
            new SecurityRule("ClassLoader|URLClassLoader", "Custom class loading is not allowed"),
            new SecurityRule("System\\.load\\s*\\(|System\\.loadLibrary\\s*\\(",
                    "Native library loading is not allowed"),
            new SecurityRule("sun\\.misc\\.Unsafe", "Unsafe access is not allowed"),
            new SecurityRule("SecurityManager", "SecurityManager manipulation is not allowed")
    );

    @Override
    public SupportedLanguage getLanguage() {
        return SupportedLanguage.JAVA;
    }

    @Override
    public List<String> compile(String sourceCode, Path workDir) throws IOException, InterruptedException {
        String className = extractClassName(sourceCode);
        Path sourceFile = workDir.resolve(className + ".java");
        Files.writeString(sourceFile, sourceCode);

        ProcessBuilder pb = new ProcessBuilder("javac", sourceFile.getFileName().toString());
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            // Strip absolute paths from error messages
            String workDirPrefix = workDir.toAbsolutePath().toString() + "/";
            return output.stream()
                    .map(line -> line.replace(workDirPrefix, ""))
                    .toList();
        }

        // Store class name for use in execution
        Files.writeString(workDir.resolve(".main_class"), className);
        return List.of();
    }

    @Override
    public String[] buildExecutionCommand(Path workDir, long memoryLimitMB) {
        String className;
        try {
            className = Files.readString(workDir.resolve(".main_class")).trim();
        } catch (IOException e) {
            className = "Main";
        }
        return new String[]{
                "java",
                "-Xmx" + memoryLimitMB + "m",
                "-Xss8m",
                "-cp", workDir.toAbsolutePath().toString(),
                className
        };
    }

    @Override
    public List<SecurityRule> getSecurityRules() {
        return securityRules;
    }

    private String extractClassName(String code) {
        Matcher m = CLASS_NAME_PATTERN.matcher(code);
        return m.find() ? m.group(1) : "Main";
    }

    private List<String> readProcessOutput(Process process) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}