package com.lightmare.villagerrealms.core.record;

public record NPCRecord(
        int dataVersion,
        Identity identity,
        Location location,
        Vitals vitals,
        NPCInventory inventory,
        EconomicState economy,
        RoleState role,
        String factionId,
        Relationships relationships,
        MemoryLog memory,
        ActionState action
) implements Versioned {

    public static final int CURRENT_VERSION = 2;

    /** Sentinel for "no faction" — used until an NPC is assigned during audit. */
    public static final String NO_FACTION = "";

    public NPCRecord {
        if (identity == null) throw new IllegalArgumentException("identity required");
        if (location == null) throw new IllegalArgumentException("location required");
        if (vitals == null) throw new IllegalArgumentException("vitals required");
        if (inventory == null) throw new IllegalArgumentException("inventory required");
        if (economy == null) throw new IllegalArgumentException("economy required");
        if (role == null) throw new IllegalArgumentException("role required");
        if (factionId == null) throw new IllegalArgumentException("factionId required (use NO_FACTION sentinel)");
        if (relationships == null) throw new IllegalArgumentException("relationships required");
        if (memory == null) throw new IllegalArgumentException("memory required");
        if (action == null) throw new IllegalArgumentException("action required");
    }

    public NPCRecord withFactionId(String newFactionId) {
        return new NPCRecord(dataVersion, identity, location, vitals, inventory,
                economy, role, newFactionId, relationships, memory, action);
    }
}
