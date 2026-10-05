package com.junseo.citymap.layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutFileTest {
    static byte[] bundled() throws IOException {
        return Files.readAllBytes(Path.of(System.getProperty("layoutFile", "../map/layout.json")));
    }

    @Test
    void createsMissingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plugin/layout.json");
        byte[] fresh = bundled();
        assertNotNull(LayoutFile.ensureCurrent(file, () -> new ByteArrayInputStream(fresh)));
        assertArrayEquals(fresh, Files.readAllBytes(file));
    }

    @Test
    void keepsSameOrNewerVersion(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("layout.json");
        byte[] fresh = bundled();
        Files.write(file, fresh);
        assertNull(LayoutFile.ensureCurrent(file, () -> new ByteArrayInputStream(fresh)), "같은 버전이면 그대로 (운영자가 고친 것 보존)");
    }

    @Test
    void replacesOlderVersionAndKeepsBackup(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("layout.json");
        byte[] fresh = bundled();
        String text = new String(fresh, StandardCharsets.UTF_8);
        int version = Layout.parse(new java.io.StringReader(text)).version();
        String old = text.replaceFirst("\"version\": " + version, "\"version\": " + (version - 1));
        Files.writeString(file, old);
        String done = LayoutFile.ensureCurrent(file, () -> new ByteArrayInputStream(fresh));
        assertNotNull(done);
        assertArrayEquals(fresh, Files.readAllBytes(file));
        Path backup = dir.resolve("layout.json.v" + (version - 1) + ".bak");
        assertTrue(Files.exists(backup));
        assertEquals(old, Files.readString(backup));
    }
}
