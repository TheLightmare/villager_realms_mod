package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.AtomicFile;
import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.Faction;
import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.FactionStoreSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single-file global store of {@link Faction} definitions plus the directed
 * faction-faction opinion matrix. Mirrors {@link VillageStore} in shape:
 * faction count is small (tens), so sharding adds nothing.
 *
 * Single-threaded: main server thread only. Disk write goes through the
 * shared {@link PersistenceWorker}.
 */
public final class FactionStore {

    private static final Logger LOG = LoggerFactory.getLogger(FactionStore.class);
    private static final String FILE_NAME = "factions.bin";

    private final Path file;
    private final PersistenceWorker worker;
    private final Map<String, Faction> byId = new HashMap<>();
    private FactionRelations relations = FactionRelations.EMPTY;
    private boolean dirty;

    public FactionStore(Path root, PersistenceWorker worker) {
        this.file = root.resolve(FILE_NAME);
        this.worker = worker;
    }

    public void load() throws IOException {
        byId.clear();
        relations = FactionRelations.EMPTY;
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

        FactionStoreSnapshot snap;
        try {
            snap = FactionStoreFormat.FORMAT.fromBytes(bytes);
        } catch (Throwable t) {
            AtomicFile.quarantine(file, t);
            return;
        }
        for (Faction f : snap.factions()) byId.put(f.id(), f);
        relations = snap.relations();
        LOG.info("FactionStore loaded: {} factions, {} relation rows",
                byId.size(), relations.by().size());
    }

    public Optional<Faction> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    public void put(Faction faction) {
        byId.put(faction.id(), faction);
        dirty = true;
    }

    public void remove(String id) {
        if (byId.remove(id) != null) dirty = true;
    }

    public Collection<Faction> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() { return byId.size(); }

    public FactionRelations relations() { return relations; }

    public void setRelations(FactionRelations newRelations) {
        if (newRelations == null) throw new IllegalArgumentException("relations required");
        if (!newRelations.equals(this.relations)) {
            this.relations = newRelations;
            this.dirty = true;
        }
    }

    public boolean isDirty() { return dirty; }

    public void flushDirty() throws IOException {
        if (!dirty) return;
        FactionStoreSnapshot snap = new FactionStoreSnapshot(
                new ArrayList<>(byId.values()), relations);
        byte[] bytes = FactionStoreFormat.FORMAT.toBytes(snap);
        worker.submit(file, bytes);
        dirty = false;
    }
}
