package com.mockwise.mockwise_backend.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LocalCodeExecutionService implements CodeExecutionService {

    private static final long DEFAULT_TIMEOUT_SECONDS = 5;
    private static final long DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final int MAX_OUTPUT_CHARS = 65_536; // 64 KB

    private final Map<SupportedLanguage, LanguageExecutor> executors;
    private final CodeSecurityValidator securityValidator;

    /**
     * Spring auto-injects every {@link LanguageExecutor} bean.
     * Adding a new language = adding a new @Component — zero changes here.
     */
    public LocalCodeExecutionService(List<LanguageExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(LanguageExecutor::getLanguage, Function.identity()));
        this.securityValidator = new CodeSecurityValidator();
        log.info("Code execution service initialized with languages: {}", executors.keySet());
    }

    @Override
    public ExecutionResult execute(String code, SupportedLanguage language) {
        return execute(code, language, DEFAULT_TIMEOUT_SECONDS, DEFAULT_MEMORY_LIMIT_MB);
    }

    @Override
    public ExecutionResult execute(String code, SupportedLanguage language,
                                   long timeoutSeconds, long memoryLimitMB) {
        LanguageExecutor executor = executors.get(language);
        if (executor == null) {
            return ExecutionResult.builder()
                    .status(ExecutionStatus.COMPILATION_ERROR)
                    .errorMessage("Unsupported language: " + language)
                    .build();
        }

        // 1. Security validation — reject dangerous code before touching disk
        Optional<String> violation = securityValidator.validate(code, executor.getSecurityRules());
        if (violation.isPresent()) {
            return ExecutionResult.builder()
                    .status(ExecutionStatus.SECURITY_VIOLATION)
                    .errorMessage(violation.get())
                    .build();
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("code_exec_");

            // 2. Compile
            List<String> compilationErrors = executor.compile(code, workDir);
            if (compilationErrors != null && !compilationErrors.isEmpty()) {
                return ExecutionResult.builder()
                        .status(ExecutionStatus.COMPILATION_ERROR)
                        .stderr(String.join("\n", compilationErrors))
                        .errorMessage(String.join("\n", compilationErrors))
                        .build();
            }

            // 3. Execute with sandbox limits
            return executeProcess(executor, workDir, timeoutSeconds, memoryLimitMB);

        } catch (Exception e) {
            log.error("Code execution failed unexpectedly", e);
            return ExecutionResult.builder()
                    .status(ExecutionStatus.RUNTIME_ERROR)
                    .errorMessage("Internal execution error: " + e.getMessage())
                    .build();
        } finally {
            // 4. Always clean up temp directory
            if (workDir != null) {
                cleanupDirectory(workDir);
            }
        }
    }

    // ---- internals --------------------------------------------------------

    private ExecutionResult executeProcess(LanguageExecutor executor, Path workDir,
                                           long timeoutSeconds, long memoryLimitMB)
            throws IOException, InterruptedException {

        String[] command = executor.buildExecutionCommand(workDir, memoryLimitMB);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());

        long startNanos = System.nanoTime();
        Process process = pb.start();

        // Close stdin so the process doesn't block waiting for input
        process.getOutputStream().close();

        // Read stdout & stderr concurrently to avoid pipe-buffer deadlock
        CompletableFuture<String> stdoutFuture = readStreamAsync(process.getInputStream());
        CompletableFuture<String> stderrFuture = readStreamAsync(process.getErrorStream());

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        long executionTimeMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            return ExecutionResult.builder()
                    .status(ExecutionStatus.TIMEOUT)
                    .executionTimeMs(executionTimeMs)
                    .stdout(getQuietly(stdoutFuture))
                    .stderr(getQuietly(stderrFuture))
                    .errorMessage("Code execution timed out after " + timeoutSeconds + " seconds")
                    .build();
        }

        String stdout = getQuietly(stdoutFuture);
        String stderr = getQuietly(stderrFuture);
        int exitCode = process.exitValue();

        // Detect OOM from exit code (137 = SIGKILL, 139 = SIGSEGV) or stderr
        if (isMemoryLimitExceeded(exitCode, stderr)) {
            return ExecutionResult.builder()
                    .status(ExecutionStatus.MEMORY_LIMIT_EXCEEDED)
                    .executionTimeMs(executionTimeMs)
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .errorMessage("Code execution exceeded memory limit of " + memoryLimitMB + " MB")
                    .build();
        }

        if (exitCode != 0) {
            return ExecutionResult.builder()
                    .status(ExecutionStatus.RUNTIME_ERROR)
                    .executionTimeMs(executionTimeMs)
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .errorMessage(stderr != null && !stderr.isEmpty() ? stderr : "Process exited with code " + exitCode)
                    .build();
        }

        return ExecutionResult.builder()
                .status(ExecutionStatus.SUCCESS)
                .executionTimeMs(executionTimeMs)
                .stdout(stdout)
                .stderr(stderr)
                .exitCode(0)
                .build();
    }

    private boolean isMemoryLimitExceeded(int exitCode, String stderr) {
        // 137 = SIGKILL (OOM killer), 139 = SIGSEGV (stack overflow)
        if (exitCode == 137 || exitCode == 139) {
            return true;
        }
        if (stderr == null) return false;
        return stderr.contains("OutOfMemoryError")
                || stderr.contains("MemoryError")
                || stderr.contains("std::bad_alloc")
                || stderr.contains("Cannot allocate memory")
                || stderr.contains("java.lang.OutOfMemoryError");
    }

    private CompletableFuture<String> readStreamAsync(InputStream is) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return readBounded(is);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private String readBounded(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null && sb.length() < MAX_OUTPUT_CHARS) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
        }
        if (sb.length() >= MAX_OUTPUT_CHARS) {
            sb.append("\n... (output truncated)");
        }
        return sb.toString();
    }

    private String getQuietly(CompletableFuture<String> future) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    private void cleanupDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory: {}", dir, e);
        }
    }
}