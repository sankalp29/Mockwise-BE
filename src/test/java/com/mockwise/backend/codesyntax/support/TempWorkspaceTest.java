package com.mockwise.backend.codesyntax.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

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
}
