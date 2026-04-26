package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPCRegistryTest {

    private PersistenceWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PersistenceWorker("test-persist");
        worker.start();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown(2000);
    }

    private void flushAndAwait(NPCRegistry reg) throws Exception {
        reg.flushDirty();
        worker.awaitIdle(5000);
    }

    @Test
    void putAndGet(@TempDir Path tmp) throws Exception {
        var reg = new NPCRegistry(tmp, worker);
        reg.load();

        UUID id = UUID.randomUUID();
        NPCRecord rec = Fixtures.npc(id, "village-1", 50, 50);
        reg.put(rec);

        assertEquals(rec, reg.get(id).orElseThrow());
        assertEquals(1, reg.size());
        assertEquals(1, reg.shardCount());
    }

    @Test
    void persistsAndReloads(@TempDir Path tmp) throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        var reg = new NPCRegistry(tmp, worker);
        reg.load();
        reg.put(Fixtures.npc(a, "village-1", 50, 50));
        reg.put(Fixtures.npc(b, "village-1", 60, 50));
        flushAndAwait(reg);

        var reg2 = new NPCRegistry(tmp, worker);
        reg2.load();
        assertEquals(2, reg2.size());
        assertTrue(reg2.get(a).isPresent());
        assertTrue(reg2.get(b).isPresent());
    }

    @Test
    void recordsInDifferentRegionsLandInDifferentShards(@TempDir Path tmp) throws Exception {
        var reg = new NPCRegistry(tmp, worker);
        reg.load();

        // Region size is 32 chunks = 512 blocks. Use coords clearly across boundaries.
        reg.put(Fixtures.npc(UUID.randomUUID(), "v1", 50, 50));        // region 0,0
        reg.put(Fixtures.npc(UUID.randomUUID(), "v1", 1000, 50));      // region 1,0
        reg.put(Fixtures.npc(UUID.randomUUID(), "v1", 50, 1000));      // region 0,1
        reg.put(Fixtures.npc(UUID.randomUUID(), "v1", -600, -600));    // region -2,-2

        assertEquals(4, reg.shardCount());
        flushAndAwait(reg);

        long shardFiles;
        try (var stream = Files.newDirectoryStream(tmp, "npcs.r.*.bin")) {
            shardFiles = java.util.stream.StreamSupport.stream(stream.spliterator(), false).count();
        }
        assertEquals(4, shardFiles);
    }

    @Test
    void corruptShardQuarantinedAndOthersSurvive(@TempDir Path tmp) throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        var reg = new NPCRegistry(tmp, worker);
        reg.load();
        reg.put(Fixtures.npc(a, "v1", 50, 50));
        reg.put(Fixtures.npc(b, "v1", 1000, 50));
        flushAndAwait(reg);

        // Corrupt one shard file by overwriting it with garbage.
        Path region00 = tmp.resolve("npcs.r.0.0.bin");
        assertTrue(Files.exists(region00));
        Files.write(region00, new byte[]{0x00, 0x00, 0x00, 0x00, 0x01});

        var reg2 = new NPCRegistry(tmp, worker);
        reg2.load();

        // The corrupt shard's record should be gone; the other survives.
        assertFalse(reg2.get(a).isPresent());
        assertTrue(reg2.get(b).isPresent());

        // Original file should be quarantined.
        assertFalse(Files.exists(region00));
        try (var stream = Files.newDirectoryStream(tmp, "npcs.r.0.0.bin.corrupt-*")) {
            assertTrue(stream.iterator().hasNext(), "expected quarantined sibling");
        }
    }

    @Test
    void removalUpdatesIndices(@TempDir Path tmp) throws Exception {
        UUID a = UUID.randomUUID();
        var reg = new NPCRegistry(tmp, worker);
        reg.load();
        reg.put(Fixtures.npc(a, "v1", 50, 50, "minecraft:overworld", "baker"));

        assertEquals(1, reg.byVillage("v1").size());
        assertEquals(1, reg.byRole("baker").size());

        reg.remove(a);

        assertTrue(reg.byVillage("v1").isEmpty());
        assertTrue(reg.byRole("baker").isEmpty());
        assertFalse(reg.get(a).isPresent());
    }

    @Test
    void emptyShardDeletedOnFlush(@TempDir Path tmp) throws Exception {
        UUID a = UUID.randomUUID();
        var reg = new NPCRegistry(tmp, worker);
        reg.load();
        reg.put(Fixtures.npc(a, "v1", 50, 50));
        flushAndAwait(reg);

        Path file = tmp.resolve("npcs.r.0.0.bin");
        assertTrue(Files.exists(file));

        reg.remove(a);
        flushAndAwait(reg);

        assertFalse(Files.exists(file));
    }

    @Test
    void byChunkFiltersWithinShard(@TempDir Path tmp) throws Exception {
        // Two chunks within the same region (region 0,0): chunk 0,0 and chunk 1,0.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        var reg = new NPCRegistry(tmp, worker);
        reg.load();
        reg.put(Fixtures.npc(a, "v1", 5, 5));     // chunk 0,0
        reg.put(Fixtures.npc(b, "v1", 8, 5));     // chunk 0,0
        reg.put(Fixtures.npc(c, "v1", 20, 5));    // chunk 1,0

        assertEquals(2, reg.byChunk(0, 0).size());
        assertEquals(1, reg.byChunk(1, 0).size());
        assertEquals(0, reg.byChunk(5, 5).size());
    }

    @Test
    void shardCodecRoundTripsManyRecords() throws Exception {
        var records = new java.util.ArrayList<NPCRecord>();
        for (int i = 0; i < 25; i++) {
            records.add(Fixtures.npc(UUID.randomUUID(), "v" + (i % 3), i * 10, 0));
        }
        byte[] bytes = NPCShardFormat.FORMAT.toBytes(records);
        List<NPCRecord> decoded = NPCShardFormat.FORMAT.fromBytes(bytes);
        assertEquals(records, decoded);
    }
}
