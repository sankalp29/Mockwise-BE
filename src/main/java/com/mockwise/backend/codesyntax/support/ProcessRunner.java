package com.mockwise.backend.codesyntax.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface ProcessRunner {
    ProcessResult run(List<String> command, Path workDir, Duration timeout) throws Exception;
}
