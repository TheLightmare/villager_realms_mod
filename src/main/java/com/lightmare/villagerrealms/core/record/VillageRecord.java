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

    /** Block X of the bounding-box centre. */
    public int centerX() { return (minX + maxX) / 2; }

    /** Block Z of the bounding-box centre. */
    public int centerZ() { return (minZ + maxZ) / 2; }

    /**
     * Ground level for "go to the village centre" targets: the median bed Y.
     * Beds sit on floors at walking height, whereas the bbox Y-midpoint is
     * usually mid-air (structures span foundations to roofs), which an NPC
     * can never get within arrival radius of. Falls back to {@code minY}
     * when there are no beds.
     */
    public int groundY() {
        return groundYOf(beds, minY);
    }

    public static int groundYOf(List<VillageBed> beds, int fallback) {
        if (beds.isEmpty()) return fallback;
        int[] ys = new int[beds.size()];
        for (int i = 0; i < ys.length; i++) ys[i] = beds.get(i).y();
        java.util.Arrays.sort(ys);
        return ys[ys.length / 2];
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
