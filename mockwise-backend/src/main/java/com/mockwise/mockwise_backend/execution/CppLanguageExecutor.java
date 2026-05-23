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
public class CppLanguageExecutor implements LanguageExecutor {

    private static final String SOURCE_NAME = "solution.cpp";
    private static final String BINARY_NAME = "solution";

    private static final Pattern BITS_STDC_PATTERN = Pattern.compile(
            "#\\s*include\\s*[<\"]\\s*bits\\s*/\\s*stdc\\s*\\+\\+\\s*\\.\\s*h\\s*[>\"]",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private static final String STANDARD_HEADERS =
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
            "#include <functional>\n" +
            "#include <sstream>\n" +
            "#include <cstring>\n" +
            "#include <cstdlib>\n";

    private final List<SecurityRule> securityRules = List.of(
            new SecurityRule("#include\\s*<fstream>", "File stream access is not allowed"),
            new SecurityRule("#include\\s*<filesystem>", "Filesystem access is not allowed"),
            new SecurityRule("#include\\s*<sys/socket\\.h>", "Network socket access is not allowed"),
            new SecurityRule("#include\\s*<netinet/", "Network access is not allowed"),
            new SecurityRule("#include\\s*<arpa/", "Network access is not allowed"),
            new SecurityRule("#include\\s*<netdb\\.h>", "Network access is not allowed"),
            new SecurityRule("\\bsystem\\s*\\(", "system() calls are not allowed"),
            new SecurityRule("\\bpopen\\s*\\(", "popen() calls are not allowed"),
            new SecurityRule("\\bfork\\s*\\(", "fork() calls are not allowed"),
            new SecurityRule("\\bexecl\\s*\\(|\\bexeclp\\s*\\(|\\bexecle\\s*\\(|\\bexecv\\s*\\(|\\bexecvp\\s*\\(|\\bexecvpe\\s*\\(",
                    "exec family calls are not allowed"),
            new SecurityRule("\\bsocket\\s*\\(", "Network socket creation is not allowed"),
            new SecurityRule("\\bconnect\\s*\\(", "Network connections are not allowed"),
            new SecurityRule("\\bfopen\\s*\\(", "C-style file access via fopen() is not allowed"),
            new SecurityRule("\\bfreopen\\s*\\(", "C-style file redirection via freopen() is not allowed")
    );

    @Override
    public SupportedLanguage getLanguage() {
        return SupportedLanguage.CPP;
    }

    @Override
    public List<String> compile(String sourceCode, Path workDir) throws IOException, InterruptedException {
        String processedCode = replaceBitsHeader(sourceCode);
        Path sourceFile = workDir.resolve(SOURCE_NAME);
        Files.writeString(sourceFile, processedCode);

        ProcessBuilder pb = new ProcessBuilder(
                "g++", "-std=c++17", "-o", BINARY_NAME, SOURCE_NAME);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String workDirPrefix = workDir.toAbsolutePath().toString() + "/";
            return output.stream()
                    .filter(line -> !line.trim().isEmpty())
                    .map(line -> line.replace(workDirPrefix, ""))
                    .toList();
        }

        return List.of();
    }

    @Override
    public String[] buildExecutionCommand(Path workDir, long memoryLimitMB) {
        // ulimit -v limits virtual memory in KB; add headroom for C++ runtime
        long ulimitKB = (memoryLimitMB + 128) * 1024;
        return new String[]{
                "/bin/sh", "-c",
                "ulimit -v " + ulimitKB + " && " + workDir.resolve(BINARY_NAME)
        };
    }

    @Override
    public List<SecurityRule> getSecurityRules() {
        return securityRules;
    }

    private String replaceBitsHeader(String code) {
        Matcher matcher = BITS_STDC_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.replaceAll(STANDARD_HEADERS);
        }
        return code;
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