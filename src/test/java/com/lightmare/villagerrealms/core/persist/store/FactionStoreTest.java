package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.Faction;
import com.lightmare.villagerrealms.core.record.FactionRelations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionStoreTest {

    private PersistenceWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PersistenceWorker("test-factions");
        worker.start();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown(2000);
    }

    @Test
    void roundTripsFactionsAndRelations(@TempDir Path tmp) throws Exception {
        var store = new FactionStore(tmp, worker);
        store.load();

        UUID leader = UUID.randomUUID();
        store.put(new Faction(Faction.CURRENT_VERSION, "faction:a", "Alpha",
                leader, Set.of("village_a")));
        store.put(new Faction(Faction.CURRENT_VERSION, "faction:b", "Beta",
                null, Set.of("village_b1", "village_b2")));
        store.setRelations(FactionRelations.EMPTY
                .withOpinion("faction:a", "faction:b", -25)
                .withOpinion("faction:b", "faction:a", 10));

        store.flushDirty();
        worker.awaitIdle(5000);
        assertTrue(Files.exists(tmp.resolve("factions.bin")));

        var reload = new FactionStore(tmp, worker);
        reload.load();
        assertEquals(2, reload.size());
        Faction a = reload.get("faction:a").orElseThrow();
        assertEquals("Alpha", a.name());
        assertEquals(leader, a.leaderUuid());
        assertEquals(Set.of("village_a"), a.claimedVillageIds());

        Faction b = reload.get("faction:b").orElseThrow();
        assertEquals(null, b.leaderUuid());
        assertEquals(Set.of("village_b1", "village_b2"), b.claimedVillageIds());

        assertEquals(-25, reload.relations().opinion("faction:a", "faction:b"));
        assertEquals(10, reload.relations().opinion("faction:b", "faction:a"));
        assertEquals(0, reload.relations().opinion("faction:a", "faction:a"));
    }

    @Test
    void cleanStoreWritesNothing(@TempDir Path tmp) throws Exception {
        var store = new FactionStore(tmp, worker);
        store.load();
        store.flushDirty();
        worker.awaitIdle(1000);
        assertFalse(Files.exists(tmp.resolve("factions.bin")));
    }

    @Test
    void corruptFileQuarantinedAndStoreEmpty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("factions.bin");
        Files.write(file, new byte[]{0x00, 0x00, 0x00, 0x00, 0x01});

        var store = new FactionStore(tmp, worker);
        store.load();

        assertEquals(0, store.size());
        assertFalse(Files.exists(file));
        try (var s = Files.newDirectoryStream(tmp, "factions.bin.corrupt-*")) {
            assertTrue(s.iterator().hasNext(), "expected quarantined sibling");
        }
    }

    @Test
    void setOpinionToZeroRemovesEntry() {
        FactionRelations r = FactionRelations.EMPTY
                .withOpinion("a", "b", 5)
                .withOpinion("a", "b", 0);
        assertEquals(0, r.opinion("a", "b"));
        assertTrue(r.by().isEmpty(), "row should be cleared when last entry hits zero");
    }
}
