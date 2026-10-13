package com.lightmare.villagerrealms.core.record;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillageRecordGeometryTest {

    private static VillageRecord village(List<VillageBed> beds) {
        return new VillageRecord(VillageRecord.CURRENT_VERSION, "v", "minecraft:overworld",
                0, 60, 0, 40, 90, 20, true, beds, List.of(), VillageMarket.EMPTY, null);
    }

    private static VillageBed bed(int y) {
        return new VillageBed(1, y, 1, "laborer", UUID.randomUUID());
    }

    @Test
    void centreIsBboxMidpointInXZ() {
        VillageRecord v = village(List.of());
        assertEquals(20, v.centerX());
        assertEquals(10, v.centerZ());
    }

    @Test
    void groundYIsMedianBedYNotBboxMidpoint() {
        VillageRecord v = village(List.of(bed(64), bed(65), bed(64), bed(72), bed(63)));
        assertEquals(64, v.groundY());
    }

    @Test
    void groundYFallsBackToMinYWithoutBeds() {
        assertEquals(60, village(List.of()).groundY());
    }
}
