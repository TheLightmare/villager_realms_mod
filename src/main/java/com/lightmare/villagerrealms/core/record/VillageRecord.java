package com.lightmare.villagerrealms.core.record;

import java.util.List;

public record VillageRecord(
        int dataVersion,
        String villageId,
        String dimension,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        boolean audited,
        List<VillageBed> beds,
        List<VillageWorkstation> workstations,
        VillageMarket market,
        String factionOwnerId
) implements Versioned {

    public static final int CURRENT_VERSION = 3;

    public VillageRecord {
        if (villageId == null) throw new IllegalArgumentException("villageId required");
        if (dimension == null) throw new IllegalArgumentException("dimension required");
        if (beds == null) throw new IllegalArgumentException("beds required");
        if (workstations == null) throw new IllegalArgumentException("workstations required");
        if (market == null) market = VillageMarket.EMPTY;
        beds = List.copyOf(beds);
        workstations = List.copyOf(workstations);
    }

    public VillageRecord withMarket(VillageMarket newMarket) {
        return new VillageRecord(
                dataVersion, villageId, dimension,
                minX, minY, minZ, maxX, maxY, maxZ,
                audited, beds, workstations, newMarket, factionOwnerId);
    }

    public VillageRecord withFactionOwner(String newFactionOwnerId) {
        return new VillageRecord(
                dataVersion, villageId, dimension,
                minX, minY, minZ, maxX, maxY, maxZ,
                audited, beds, workstations, market, newFactionOwnerId);
    }
}
