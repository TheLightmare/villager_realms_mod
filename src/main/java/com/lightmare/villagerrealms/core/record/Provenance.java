package com.lightmare.villagerrealms.core.record;

public enum Provenance {
    CRAFTED,
    LOOTED,
    BOUGHT,
    GIFTED,
    SPAWNED,
    UNKNOWN;

    private static final Provenance[] VALUES = values();

    public static Provenance byOrdinal(int o) {
        if (o < 0 || o >= VALUES.length) {
            throw new IllegalArgumentException("Provenance ordinal out of range: " + o);
        }
        return VALUES[o];
    }
}
