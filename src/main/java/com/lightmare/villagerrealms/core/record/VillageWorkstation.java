package com.lightmare.villagerrealms.core.record;

public record VillageWorkstation(
        int x, int y, int z,
        String role
) {
    public VillageWorkstation {
        if (role == null) throw new IllegalArgumentException("role required");
    }
}
