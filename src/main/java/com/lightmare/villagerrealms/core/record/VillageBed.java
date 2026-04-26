package com.lightmare.villagerrealms.core.record;

import java.util.UUID;

public record VillageBed(
        int x, int y, int z,
        String role,
        UUID occupant
) {
    public VillageBed {
        if (role == null) throw new IllegalArgumentException("role required");
    }
}
