package com.mockwise.backend.service.codesyntax.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class TempWorkspaceTest {

    @Test
    void createsAndDeletesDirectory() throws Exception {
        var pathHolder = new Object() { java.nio.file.Path path; };
        try (TempWorkspace ws = TempWorkspace.create("syntax_test_")) {
            pathHolder.path = ws.path();
            assertTrue(Files.isDirectory(ws.path()));
            Files.writeString(ws.path().resolve("f.txt"), "x");
        }
        assertFalse(Files.exists(pathHolder.path));
    }

    @Test
    @EnabledOnOs({OS.MAC, OS.LINUX})
    void directoryIsReadableOnlyByItsOwner() throws Exception {
        try (TempWorkspace workspace = TempWorkspace.create("syntax_perm_")) {
            assertEquals(
                    PosixFilePermissions.fromString("rwx------"),
                    Files.getPosixFilePermissions(workspace.path()));
        }
    }
}
