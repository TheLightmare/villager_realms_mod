package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.ai.RecordVillageView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageBedReservationTest {

    private static VillageRecord village(VillageBed... beds) {
        return new VillageRecord(VillageRecord.CURRENT_VERSION, "v", "minecraft:overworld",
                0, 60, 0, 40, 90, 20, true, List.of(beds), List.of(), VillageMarket.EMPTY, "faction:v", List.of());
    }

    @Test
    void legacyConstructorMeansNoReservation() {
        VillageBed b = new VillageBed(1, 64, 1, "farmer", null);
        assertNull(b.reservedFor());
        assertTrue(b.isFree());
        assertFalse(b.isReserved());
    }

    @Test
    void reservationMakesABedNotFreeAndIsCountedByTheView() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(
                new VillageBed(1, 64, 1, "farmer", UUID.randomUUID()),
                new VillageBed(2, 64, 2, "laborer", null),
                new VillageBed(3, 64, 3, "laborer", null));
        assertEquals(2, v.freeBedCount());
        assertEquals(1, v.firstFreeBedIndex());

        VillageRecord r = v.reserveBed(1, child);
        assertEquals(1, r.freeBedCount());
        assertEquals(2, r.firstFreeBedIndex());
        assertEquals(1, r.reservedBedIndexOf(child));
        assertEquals(1, new RecordVillageView(r).freeBeds(), "the view must not offer a reserved bed");
        assertThrows(IllegalStateException.class, () -> r.reserveBed(1, UUID.randomUUID()));
    }

    @Test
    void occupyReservedTurnsReservationIntoOccupancy() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(new VillageBed(2, 64, 2, "baker", null)).reserveBed(0, child);
        VillageRecord o = v.occupyReserved(child);
        assertEquals(child, o.beds().get(0).occupant());
        assertNull(o.beds().get(0).reservedFor());
        assertEquals(0, o.bedIndexOf(child));
        assertEquals(0, o.freeBedCount());
    }

    @Test
    void occupyFallsBackToAnyFreeBedWhenReservationWasLostAndNullWhenNone() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(new VillageBed(2, 64, 2, "baker", null));
        VillageRecord o = v.occupyReserved(child);
        assertEquals(child, o.beds().get(0).occupant());

        VillageRecord full = village(new VillageBed(2, 64, 2, "baker", UUID.randomUUID()));
        assertNull(full.occupyReserved(child));
    }

    @Test
    void releaseReservationFreesTheBed() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(new VillageBed(2, 64, 2, "baker", null)).reserveBed(0, child);
        VillageRecord r = v.releaseReservation(child);
        assertTrue(r.beds().get(0).isFree());
        assertSame(r, r.releaseReservation(UUID.randomUUID()), "unknown child → no change");
    }

    @Test
    void deathOfAnOccupantKeepsAnUnrelatedReservationIntact() {
        UUID dead = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        VillageRecord v = village(
                new VillageBed(1, 64, 1, "farmer", dead),
                new VillageBed(2, 64, 2, "laborer", null)).reserveBed(1, child);
        VillageRecord after = v.recordDeath(new DeathRecord(dead, "Tom", "farmer", 1L, "player", null));
        assertTrue(after.beds().get(0).isFree());
        assertEquals(child, after.beds().get(1).reservedFor());
    }
}
