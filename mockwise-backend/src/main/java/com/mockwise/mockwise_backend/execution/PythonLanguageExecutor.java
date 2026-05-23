package com.mockwise.mockwise_backend.execution;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PythonLanguageExecutor implements LanguageExecutor {

    private static final String SCRIPT_NAME = "solution.py";

    private final List<SecurityRule> securityRules = List.of(
            new SecurityRule("\\bimport\\s+os\\b", "os module is not allowed"),
            new SecurityRule("\\bfrom\\s+os\\b", "os module is not allowed"),
            new SecurityRule("\\bimport\\s+subprocess\\b", "subprocess module is not allowed"),
            new SecurityRule("\\bimport\\s+socket\\b", "Network socket access is not allowed"),
            new SecurityRule("\\bimport\\s+http\\b", "HTTP module is not allowed"),
            new SecurityRule("\\bfrom\\s+http\\b", "HTTP module is not allowed"),
            new SecurityRule("\\bimport\\s+urllib\\b", "URL library is not allowed"),
            new SecurityRule("\\bfrom\\s+urllib\\b", "URL library is not allowed"),
            new SecurityRule("\\bimport\\s+requests\\b", "requests module is not allowed"),
            new SecurityRule("\\bimport\\s+shutil\\b", "shutil module is not allowed"),
            new SecurityRule("\\bimport\\s+pathlib\\b", "pathlib module is not allowed"),
            new SecurityRule("\\bimport\\s+glob\\b", "glob module is not allowed"),
            new SecurityRule("\\bimport\\s+tempfile\\b", "tempfile module is not allowed"),
            new SecurityRule("\\bimport\\s+signal\\b", "signal module is not allowed"),
            new SecurityRule("\\bimport\\s+ctypes\\b", "ctypes module is not allowed"),
            new SecurityRule("\\bimport\\s+multiprocessing\\b", "multiprocessing module is not allowed"),
            new SecurityRule("\\bimport\\s+threading\\b", "threading module is not allowed"),
            new SecurityRule("\\bopen\\s*\\(", "File access via open() is not allowed"),
            new SecurityRule("\\bexec\\s*\\(", "Dynamic code execution via exec() is not allowed"),
            new SecurityRule("\\beval\\s*\\(", "Dynamic code execution via eval() is not allowed"),
            new SecurityRule("\\b__import__\\s*\\(", "Dynamic imports via __import__() are not allowed"),
            new SecurityRule("\\bimport\\s+importlib\\b", "importlib module is not allowed"),
            new SecurityRule("\\bimport\\s+sys\\b", "sys module is not allowed"),
            new SecurityRule("\\bfrom\\s+sys\\b", "sys module is not allowed")
    );

    @Override
    public SupportedLanguage getLanguage() {
        return SupportedLanguage.PYTHON;
    }

    @Override
    public List<String> compile(String sourceCode, Path workDir) throws IOException, InterruptedException {
        Path sourceFile = workDir.resolve(SCRIPT_NAME);
        Files.writeString(sourceFile, sourceCode);

        // Use py_compile for syntax checking
        String pythonCmd = findPythonCommand();
        ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-m", "py_compile", SCRIPT_NAME);
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
        // ulimit -v limits virtual memory in KB; add headroom for Python runtime
        long ulimitKB = (memoryLimitMB + 256) * 1024;
        String pythonCmd = findPythonCommand();
        return new String[]{
                "/bin/sh", "-c",
                "ulimit -v " + ulimitKB + " && " + pythonCmd + " " + workDir.resolve(SCRIPT_NAME)
        };
    }

    @Override
    public List<SecurityRule> getSecurityRules() {
        return securityRules;
    }

    private String findPythonCommand() {
        for (String cmd : List.of("python3", "python")) {
            try {
                Process p = new ProcessBuilder("which", cmd)
                        .redirectErrorStream(true).start();
                if (p.waitFor() == 0) return cmd;
            } catch (Exception ignored) {
            }
        }
        return "python3"; // fallback
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