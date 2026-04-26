package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.AtomicFile;
import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UUID -> NPCRecord registry, sharded on disk by region (regionX, regionZ).
 *
 * Single-threaded: all access must be on the main server thread. The only
 * background work is the disk write inside {@link PersistenceWorker}.
 *
 * Indices ({@code byVillage}, {@code byRole}) are derived in-memory; never
 * persisted.
 */
public final class NPCRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(NPCRegistry.class);
    private static final Pattern SHARD_NAME =
            Pattern.compile("npcs\\.r\\.(-?\\d+)\\.(-?\\d+)\\.bin");

    public record ShardKey(int regionX, int regionZ) {
        public String filename() { return "npcs.r." + regionX + "." + regionZ + ".bin"; }

        public static ShardKey of(NPCRecord rec) {
            Location loc = rec.location();
            return new ShardKey(loc.regionX(), loc.regionZ());
        }
    }

    private final Path root;
    private final PersistenceWorker worker;

    private final Map<UUID, NPCRecord> byUuid = new HashMap<>();
    private final Map<ShardKey, Set<UUID>> byShard = new HashMap<>();
    private final Set<ShardKey> dirty = new HashSet<>();

    private final Map<String, Set<UUID>> byVillage = new HashMap<>();
    private final Map<String, Set<UUID>> byRole = new HashMap<>();

    public NPCRegistry(Path root, PersistenceWorker worker) {
        this.root = root;
        this.worker = worker;
    }

    public void load() throws IOException {
        byUuid.clear();
        byShard.clear();
        dirty.clear();
        byVillage.clear();
        byRole.clear();

        if (!Files.exists(root)) {
            Files.createDirectories(root);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "npcs.r.*.bin")) {
            for (Path file : stream) {
                Matcher m = SHARD_NAME.matcher(file.getFileName().toString());
                if (!m.matches()) continue;
                int rx = Integer.parseInt(m.group(1));
                int rz = Integer.parseInt(m.group(2));
                ShardKey key = new ShardKey(rx, rz);

                byte[] bytes;
                try {
                    bytes = AtomicFile.readOrNull(file);
                } catch (IOException e) {
                    LOG.error("I/O error reading shard {}: {}", file, e.toString(), e);
                    AtomicFile.quarantine(file, e);
                    continue;
                }
                if (bytes == null) continue;

                List<NPCRecord> records;
                try {
                    records = NPCShardFormat.FORMAT.fromBytes(bytes);
                } catch (Throwable t) {
                    AtomicFile.quarantine(file, t);
                    continue;
                }

                for (NPCRecord rec : records) {
                    addToMaps(key, rec);
                }
            }
        }

        LOG.info("NPCRegistry loaded: {} records across {} shards",
                byUuid.size(), byShard.size());
    }

    public Optional<NPCRecord> get(UUID id) {
        return Optional.ofNullable(byUuid.get(id));
    }

    public Collection<NPCRecord> all() {
        return Collections.unmodifiableCollection(byUuid.values());
    }

    public Collection<NPCRecord> byVillage(String villageId) {
        Set<UUID> ids = byVillage.get(villageId);
        if (ids == null || ids.isEmpty()) return List.of();
        var out = new ArrayList<NPCRecord>(ids.size());
        for (UUID id : ids) out.add(byUuid.get(id));
        return out;
    }

    public Collection<NPCRecord> byRole(String roleId) {
        Set<UUID> ids = byRole.get(roleId);
        if (ids == null || ids.isEmpty()) return List.of();
        var out = new ArrayList<NPCRecord>(ids.size());
        for (UUID id : ids) out.add(byUuid.get(id));
        return out;
    }

    public Collection<NPCRecord> byChunk(int chunkX, int chunkZ) {
        int rx = chunkX >> 5;
        int rz = chunkZ >> 5;
        Set<UUID> ids = byShard.get(new ShardKey(rx, rz));
        if (ids == null || ids.isEmpty()) return List.of();
        var out = new ArrayList<NPCRecord>();
        for (UUID id : ids) {
            NPCRecord rec = byUuid.get(id);
            if (rec == null) continue;
            Location loc = rec.location();
            if (loc.chunkX() == chunkX && loc.chunkZ() == chunkZ) out.add(rec);
        }
        return out;
    }

    public void put(NPCRecord rec) {
        UUID id = rec.identity().uuid();
        ShardKey newKey = ShardKey.of(rec);

        NPCRecord prior = byUuid.put(id, rec);
        if (prior != null) {
            ShardKey oldKey = ShardKey.of(prior);
            if (!oldKey.equals(newKey)) {
                Set<UUID> oldSet = byShard.get(oldKey);
                if (oldSet != null) {
                    oldSet.remove(id);
                    if (oldSet.isEmpty()) byShard.remove(oldKey);
                    dirty.add(oldKey);
                }
            }
            removeFromIndex(byVillage, prior.location().homeVillageId(), id);
            removeFromIndex(byRole, prior.role().roleId(), id);
        }

        byShard.computeIfAbsent(newKey, k -> new HashSet<>()).add(id);
        dirty.add(newKey);

        addToIndex(byVillage, rec.location().homeVillageId(), id);
        addToIndex(byRole, rec.role().roleId(), id);
    }

    public void remove(UUID id) {
        NPCRecord prior = byUuid.remove(id);
        if (prior == null) return;
        ShardKey key = ShardKey.of(prior);
        Set<UUID> set = byShard.get(key);
        if (set != null) {
            set.remove(id);
            if (set.isEmpty()) byShard.remove(key);
        }
        dirty.add(key);
        removeFromIndex(byVillage, prior.location().homeVillageId(), id);
        removeFromIndex(byRole, prior.role().roleId(), id);
    }

    /**
     * Snapshot dirty shards on the calling (main) thread, then hand the bytes
     * to the persistence worker for atomic write off-thread.
     */
    public void flushDirty() throws IOException {
        if (dirty.isEmpty()) return;
        for (ShardKey key : dirty) {
            Path target = root.resolve(key.filename());
            Set<UUID> ids = byShard.get(key);
            if (ids == null || ids.isEmpty()) {
                if (Files.exists(target)) Files.delete(target);
                continue;
            }
            var records = new ArrayList<NPCRecord>(ids.size());
            for (UUID id : ids) records.add(byUuid.get(id));
            byte[] bytes = NPCShardFormat.FORMAT.toBytes(records);
            worker.submit(target, bytes);
        }
        dirty.clear();
    }

    public int size() { return byUuid.size(); }
    public int shardCount() { return byShard.size(); }
    public Set<ShardKey> dirtyShards() { return Collections.unmodifiableSet(dirty); }

    private void addToMaps(ShardKey key, NPCRecord rec) {
        UUID id = rec.identity().uuid();
        byUuid.put(id, rec);
        byShard.computeIfAbsent(key, k -> new HashSet<>()).add(id);
        addToIndex(byVillage, rec.location().homeVillageId(), id);
        addToIndex(byRole, rec.role().roleId(), id);
    }

    private static void addToIndex(Map<String, Set<UUID>> idx, String key, UUID id) {
        if (key == null) return;
        idx.computeIfAbsent(key, k -> new HashSet<>()).add(id);
    }

    private static void removeFromIndex(Map<String, Set<UUID>> idx, String key, UUID id) {
        if (key == null) return;
        Set<UUID> set = idx.get(key);
        if (set == null) return;
        set.remove(id);
        if (set.isEmpty()) idx.remove(key);
    }
}
