package com.lightmare.villagerrealms.core.record;

import java.util.UUID;

/**
 * A bed in a village. {@code occupant} is the NPC who sleeps here;
 * {@code reservedFor} is an unborn child's pre-generated uuid, set at
 * conception so two couples can't both count the same free bed (CLAUDE.md:
 * "bed reservation on conception"). A bed is free only when both are null.
 */
public record VillageBed(
        int x, int y, int z,
        String role,
        UUID occupant,
        UUID reservedFor
) {
    public VillageBed {
        if (role == null) throw new IllegalArgumentException("role required");
    }

    public VillageBed(int x, int y, int z, String role, UUID occupant) {
        this(x, y, z, role, occupant, null);
    }

    public VillageBed withOccupant(UUID newOccupant) {
        return new VillageBed(x, y, z, role, newOccupant, reservedFor);
    }

    public VillageBed withReservation(UUID child) {
        return new VillageBed(x, y, z, role, occupant, child);
    }

    public boolean isFree() { return occupant == null && reservedFor == null; }

    public boolean isReserved() { return reservedFor != null; }
}
