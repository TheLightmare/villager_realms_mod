package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.VillageWorkstation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageStoreTest {

    private PersistenceWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PersistenceWorker("test-villages");
        worker.start();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown(2000);
    }

    private VillageRecord sample(String id, boolean audited) {
        return new VillageRecord(
                VillageRecord.CURRENT_VERSION,
                id,
                "minecraft:overworld",
                -10, 60, -10, 30, 75, 30,
                audited,
                List.of(
                        new VillageBed(0, 64, 0, "farmer", UUID.randomUUID()),
                        new VillageBed(5, 64, 5, "laborer", UUID.randomUUID())),
                List.of(new VillageWorkstation(2, 64, 2, "farmer")),
                VillageMarket.EMPTY,
                "faction:" + id);
    }

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        var store = new VillageStore(tmp, worker);
        store.load();
        VillageRecord v = sample("village_a", true);
        store.put(v);

        store.flushDirty();
        worker.awaitIdle(5000);
        assertTrue(Files.exists(tmp.resolve("villages.bin")));

        var store2 = new VillageStore(tmp, worker);
        store2.load();
        assertEquals(1, store2.size());
        assertEquals(v, store2.get("village_a").orElseThrow());
        assertTrue(store2.isAudited("village_a"));
    }

    @Test
    void nothingToFlushWhenClean(@TempDir Path tmp) throws Exception {
        var store = new VillageStore(tmp, worker);
        store.load();
        store.flushDirty();
        worker.awaitIdle(1000);
        assertFalse(Files.exists(tmp.resolve("villages.bin")));
    }

    @Test
    void corruptFileQuarantinedAndStoreEmpty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("villages.bin");
        Files.write(file, new byte[]{0x00, 0x00, 0x00, 0x00, 0x01});

        var store = new VillageStore(tmp, worker);
        store.load();

        assertEquals(0, store.size());
        assertFalse(Files.exists(file));
        try (var s = Files.newDirectoryStream(tmp, "villages.bin.corrupt-*")) {
            assertTrue(s.iterator().hasNext(), "expected quarantined sibling");
        }
    }
}
