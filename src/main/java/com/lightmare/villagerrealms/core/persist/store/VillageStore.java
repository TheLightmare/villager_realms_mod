package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.AtomicFile;
import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single-file global store of {@link VillageRecord}s, keyed by villageId.
 *
 * Villages are sparse compared to NPCs (typically tens, not thousands), so
 * sharding adds no value here. One file is fine.
 *
 * Single-threaded: main server thread only. Disk write is via the shared
 * PersistenceWorker.
 */
public final class VillageStore {

    private static final Logger LOG = LoggerFactory.getLogger(VillageStore.class);
    private static final String FILE_NAME = "villages.bin";

    private final Path file;
    private final PersistenceWorker worker;
    private final Map<String, VillageRecord> byId = new HashMap<>();
    private boolean dirty;

    public VillageStore(Path root, PersistenceWorker worker) {
        this.file = root.resolve(FILE_NAME);
        this.worker = worker;
    }

    public void load() throws IOException {
        byId.clear();
        dirty = false;

        Path dir = file.getParent();
        if (dir != null) Files.createDirectories(dir);
        if (!Files.exists(file)) return;

        byte[] bytes;
        try {
            bytes = AtomicFile.readOrNull(file);
        } catch (IOException e) {
            LOG.error("I/O error reading {}: {}", file, e.toString(), e);
            AtomicFile.quarantine(file, e);
            return;
        }
        if (bytes == null) return;

        List<VillageRecord> records;
        try {
            records = VillageStoreFormat.FORMAT.fromBytes(bytes);
        } catch (Throwable t) {
            AtomicFile.quarantine(file, t);
            return;
        }
        for (VillageRecord rec : records) byId.put(rec.villageId(), rec);
        LOG.info("VillageStore loaded: {} villages", byId.size());
    }

    public Optional<VillageRecord> get(String villageId) {
        return Optional.ofNullable(byId.get(villageId));
    }

    public boolean isAudited(String villageId) {
        VillageRecord rec = byId.get(villageId);
        return rec != null && rec.audited();
    }

    public void put(VillageRecord rec) {
        byId.put(rec.villageId(), rec);
        dirty = true;
    }

    public void remove(String villageId) {
        if (byId.remove(villageId) != null) dirty = true;
    }

    public Collection<VillageRecord> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() { return byId.size(); }
    public boolean isDirty() { return dirty; }

    public void flushDirty() throws IOException {
        if (!dirty) return;
        var snapshot = new ArrayList<>(byId.values());
        byte[] bytes = VillageStoreFormat.FORMAT.toBytes(snapshot);
        worker.submit(file, bytes);
        dirty = false;
    }
}
