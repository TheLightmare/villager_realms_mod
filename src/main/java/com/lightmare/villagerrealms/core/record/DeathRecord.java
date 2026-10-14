package com.lightmare.villagerrealms.core.record;

import java.util.UUID;

/**
 * A village's memory of one of its own dying. The NPC's record is removed
 * from the registry on death; this is what remains. {@code killerRef} is a
 * subject reference ({@code player:<uuid>}, {@code entity:<type>}, …) or
 * null when nobody is to blame (starvation, environment).
 */
public record DeathRecord(
        UUID uuid,
        String name,
        String roleId,
        long tick,
        String cause,
        String killerRef
) {
    public DeathRecord {
        if (uuid == null) throw new IllegalArgumentException("uuid required");
        if (cause == null || cause.isEmpty()) throw new IllegalArgumentException("cause required");
        if (name == null) name = "";
        if (roleId == null) roleId = "";
    }

    public static DeathRecord of(NPCRecord npc, long tick, String cause, String killerRef) {
        return new DeathRecord(
                npc.identity().uuid(), npc.identity().name(),
                npc.role() == null ? "" : npc.role().roleId(),
                tick, cause, killerRef);
    }
}
