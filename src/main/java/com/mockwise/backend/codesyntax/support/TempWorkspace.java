package com.mockwise.backend.codesyntax.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Auto-cleaning temporary directory for a single syntax-check run.
 */
public final class TempWorkspace implements AutoCloseable {

    private final Path path;

    private TempWorkspace(Path path) {
        this.path = path;
    }

    public static TempWorkspace create(String prefix) throws IOException {
        return new TempWorkspace(Files.createTempDirectory(prefix));
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // best-effort cleanup
                            }
                        });
            }
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
