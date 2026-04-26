package com.lightmare.villagerrealms.core.record;

public record Location(
        String homeVillageId,
        double x,
        double y,
        double z,
        String dimension,
        Tier tier
) {
    public Location {
        if (dimension == null) throw new IllegalArgumentException("dimension required");
        if (tier == null) throw new IllegalArgumentException("tier required");
    }

    public int chunkX() { return (int) Math.floor(x) >> 4; }
    public int chunkZ() { return (int) Math.floor(z) >> 4; }

    public int regionX() { return chunkX() >> 5; }
    public int regionZ() { return chunkZ() >> 5; }
}
