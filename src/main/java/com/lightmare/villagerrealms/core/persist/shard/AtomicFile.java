package com.lightmare.villagerrealms.core.persist.shard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class AtomicFile {

    private static final Logger LOG = LoggerFactory.getLogger(AtomicFile.class);

    private AtomicFile() {}

    public static void write(Path target, byte[] bytes) throws IOException {
        Path dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        try (FileChannel ch = FileChannel.open(tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            ch.write(java.nio.ByteBuffer.wrap(bytes));
            ch.force(true);
        }

        try {
            Files.move(tmp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException atomicNotSupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static byte[] readOrNull(Path target) throws IOException {
        if (!Files.exists(target)) return null;
        return Files.readAllBytes(target);
    }

    public static Path quarantine(Path target, Throwable cause) {
        if (!Files.exists(target)) return null;
        String stamp = Instant.now().toString().replace(':', '-');
        Path corrupt = target.resolveSibling(target.getFileName() + ".corrupt-" + stamp);
        try {
            Files.move(target, corrupt, StandardCopyOption.REPLACE_EXISTING);
            LOG.error("Quarantined corrupt shard {} -> {}: {}",
                    target, corrupt, cause == null ? "no cause" : cause.toString());
        } catch (IOException e) {
            LOG.error("Failed to quarantine corrupt shard {}: {}", target, e.toString(), e);
        }
        return corrupt;
    }
}
