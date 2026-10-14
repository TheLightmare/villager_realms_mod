package com.lightmare.villagerrealms.core.record;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageRecordDeathTest {

    private static VillageRecord village(List<VillageBed> beds, List<DeathRecord> deaths) {
        return new VillageRecord(VillageRecord.CURRENT_VERSION, "v", "minecraft:overworld",
                0, 60, 0, 40, 90, 20, true, beds, List.of(), VillageMarket.EMPTY, "faction:v", deaths);
    }

    @Test
    void recordDeathFreesTheOccupiedBedAndLedgersIt() {
        UUID dead = UUID.randomUUID();
        UUID alive = UUID.randomUUID();
        VillageRecord v = village(List.of(
                new VillageBed(1, 64, 1, "farmer", dead),
                new VillageBed(2, 64, 2, "baker", alive)), List.of());

        DeathRecord d = new DeathRecord(dead, "Tom", "farmer", 500L, "player", "player:" + UUID.randomUUID());
        VillageRecord after = v.recordDeath(d);

        assertNull(after.beds().get(0).occupant(), "the dead NPC's bed is freed");
        assertTrue(after.beds().get(0).isFree());
        assertEquals(alive, after.beds().get(1).occupant(), "other beds untouched");
        assertEquals("farmer", after.beds().get(0).role(), "bed keeps its role assignment");
        assertEquals(List.of(d), after.deaths());
        assertTrue(after.isDead(dead));
        assertFalse(after.isDead(alive));
        assertFalse(after.isDead(null));
    }

    @Test
    void ledgerIsCappedToMostRecent() {
        List<DeathRecord> many = new ArrayList<>();
        for (int i = 0; i < VillageRecord.MAX_DEATHS + 10; i++) {
            many.add(new DeathRecord(UUID.randomUUID(), "n" + i, "", i, "starvation", null));
        }
        VillageRecord v = village(List.of(), many);
        assertEquals(VillageRecord.MAX_DEATHS, v.deaths().size());
        assertEquals(many.get(10), v.deaths().get(0), "oldest entries roll off");

        VillageRecord more = v.recordDeath(new DeathRecord(UUID.randomUUID(), "x", "", 99999L, "player", null));
        assertEquals(VillageRecord.MAX_DEATHS, more.deaths().size());
        assertEquals(99999L, more.deaths().get(more.deaths().size() - 1).tick());
    }

    @Test
    void deathRecordOfCopiesIdentityAndRole() {
        NPCRecord npc = com.lightmare.villagerrealms.core.Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        DeathRecord d = DeathRecord.of(npc, 42L, "starvation", null);
        assertEquals(npc.identity().uuid(), d.uuid());
        assertEquals(npc.identity().name(), d.name());
        assertEquals("farmer", d.roleId());
        assertEquals(42L, d.tick());
        assertNull(d.killerRef());
    }
}
