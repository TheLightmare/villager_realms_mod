package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.AtomicFile;
import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.ScheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The persisted simulation schedule: a single-file global queue of
 * {@link ScheduledEvent}s keyed by id. Small (one entry per pending birth
 * or age-up), so no sharding and no index beyond the map; {@link #due}
 * scans and sorts, which is fine at these sizes.
 *
 * Single-threaded: main server thread only. Disk write goes through the
 * shared {@link PersistenceWorker}.
 */
public final class ScheduleStore {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduleStore.class);
    private static final String FILE_NAME = "schedule.bin";

    private final Path file;
    private final PersistenceWorker worker;
    private final Map<UUID, ScheduledEvent> byId = new HashMap<>();
    private boolean dirty;

    public ScheduleStore(Path root, PersistenceWorker worker) {
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

        List<ScheduledEvent> events;
        try {
            events = ScheduleStoreFormat.FORMAT.fromBytes(bytes);
        } catch (Throwable t) {
            AtomicFile.quarantine(file, t);
            return;
        }
        for (ScheduledEvent e : events) byId.put(e.id(), e);
        LOG.info("ScheduleStore loaded: {} pending events", byId.size());
    }

    public Optional<ScheduledEvent> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public void put(ScheduledEvent event) {
        byId.put(event.id(), event);
        dirty = true;
    }

    public void remove(UUID id) {
        if (byId.remove(id) != null) dirty = true;
    }

    public Collection<ScheduledEvent> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Events whose dueTick ≤ {@code now}, earliest first. A fresh list; safe to mutate the store while iterating. */
    public List<ScheduledEvent> due(long now) {
        List<ScheduledEvent> out = new ArrayList<>();
        for (ScheduledEvent e : byId.values()) if (e.dueTick() <= now) out.add(e);
        out.sort(Comparator.comparingLong(ScheduledEvent::dueTick));
        return out;
    }

    /** Pending events of {@code kind} whose subject is {@code subject}. */
    public List<ScheduledEvent> forSubject(UUID subject, String kind) {
        List<ScheduledEvent> out = new ArrayList<>();
        for (ScheduledEvent e : byId.values()) {
            if (subject.equals(e.subject()) && (kind == null || kind.equals(e.kind()))) out.add(e);
        }
        return out;
    }

    public int size() { return byId.size(); }
    public boolean isDirty() { return dirty; }

    public void flushDirty() throws IOException {
        if (!dirty) return;
        byte[] bytes = ScheduleStoreFormat.FORMAT.toBytes(new ArrayList<>(byId.values()));
        worker.submit(file, bytes);
        dirty = false;
    }
}
