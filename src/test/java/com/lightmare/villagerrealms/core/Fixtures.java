package com.lightmare.villagerrealms.core;

import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.Debt;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.PropertyRef;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.Traits;
import com.lightmare.villagerrealms.core.record.Vitals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Fixtures {

    private Fixtures() {}

    public static NPCRecord npc(UUID id, String village, double x, double z) {
        return npc(id, village, x, z, "minecraft:overworld", "farmer");
    }

    public static NPCRecord npc(UUID id, String village, double x, double z, String dim, String role) {
        return new NPCRecord(
                NPCRecord.CURRENT_VERSION,
                new Identity(id, "Test-" + id.toString().substring(0, 6), 30, Gender.OTHER, Traits.NEUTRAL),
                new Location(village, x, 64.0, z, dim, Tier.NEARBY),
                Vitals.FRESH,
                new NPCInventory(List.of(
                        new ItemEntry("minecraft:bread", 3, Provenance.CRAFTED, 100L),
                        new ItemEntry("minecraft:emerald", 5, Provenance.BOUGHT, 200L))),
                new EconomicState(
                        50L,
                        List.of(new Debt(UUID.randomUUID(), 12L, 50L)),
                        List.of(new PropertyRef(village, "house-1"))),
                new RoleState(role, "workstation:" + role + "@0,64,0", 1234L),
                "faction:" + village,
                new Relationships(
                        Map.of("faction:other", -5),
                        Map.of(UUID.randomUUID(), -10, UUID.randomUUID(), 25)),
                new MemoryLog(8, List.of(new MemoryEvent(500L, "GIFT", null, "received bread"))),
                new ActionState("idle", 0, 0L, ""));
    }
}
