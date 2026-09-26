package com.mockwise.backend.service.codesyntax.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Auto-cleaning temporary directory for a single syntax-check run.
 */
public final class TempWorkspace implements AutoCloseable {

    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    private final Path path;

    private TempWorkspace(Path path) {
        this.path = path;
    }

    public static TempWorkspace create(String prefix) throws IOException {
        return new TempWorkspace(Files.createTempDirectory(prefix, OWNER_ONLY));
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(path)) {
            List<Path> toDelete = entries.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : toDelete) {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
