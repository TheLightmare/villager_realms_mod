package com.lightmare.villagerrealms.core.persist.shard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileTest {

    @Test
    void writeThenReadRoundTrip(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("a/b/c.bin");
        byte[] payload = "hello".getBytes();

        AtomicFile.write(target, payload);
        assertArrayEquals(payload, AtomicFile.readOrNull(target));
    }

    @Test
    void readMissingFileReturnsNull(@TempDir Path tmp) throws IOException {
        assertNull(AtomicFile.readOrNull(tmp.resolve("nope.bin")));
    }

    @Test
    void writeOverwritesExisting(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("file.bin");
        AtomicFile.write(target, new byte[]{1, 2, 3});
        AtomicFile.write(target, new byte[]{4, 5, 6, 7});
        assertArrayEquals(new byte[]{4, 5, 6, 7}, AtomicFile.readOrNull(target));
    }

    @Test
    void noTmpFileLeftAfterSuccessfulWrite(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("file.bin");
        AtomicFile.write(target, new byte[]{1, 2, 3});
        Path tmpFile = target.resolveSibling("file.bin.tmp");
        assertFalse(Files.exists(tmpFile));
    }

    @Test
    void quarantineRenamesFileWithCorruptSuffix(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("file.bin");
        Files.write(target, new byte[]{0xb, 0xa, 0xd});

        Path corrupt = AtomicFile.quarantine(target, new RuntimeException("synthetic"));

        assertNotNull(corrupt);
        assertFalse(Files.exists(target));
        assertTrue(Files.exists(corrupt));
        assertTrue(corrupt.getFileName().toString().contains(".corrupt-"));
    }

    @Test
    void quarantineMissingFileNoOp(@TempDir Path tmp) {
        Path corrupt = AtomicFile.quarantine(tmp.resolve("nope.bin"), null);
        assertNull(corrupt);
    }
}
