package com.lightmare.villagerrealms.core.record;

import java.util.Set;
import java.util.UUID;

public record Faction(
        int dataVersion,
        String id,
        String name,
        UUID leaderUuid,
        Set<String> claimedVillageIds
) implements Versioned {

    public static final int CURRENT_VERSION = 1;

    public Faction {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id required");
        if (name == null) throw new IllegalArgumentException("name required");
        if (claimedVillageIds == null) throw new IllegalArgumentException("claimedVillageIds required");
        claimedVillageIds = Set.copyOf(claimedVillageIds);
    }

    public Faction withClaimedVillages(Set<String> villages) {
        return new Faction(dataVersion, id, name, leaderUuid, villages);
    }

    public Faction withLeader(UUID leader) {
        return new Faction(dataVersion, id, name, leader, claimedVillageIds);
    }
}
