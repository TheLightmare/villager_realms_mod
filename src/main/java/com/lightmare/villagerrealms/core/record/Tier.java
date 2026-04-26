package com.lightmare.villagerrealms.core.record;

public enum Tier {
    ACTIVE,
    NEARBY,
    DORMANT,
    COLD;

    private static final Tier[] VALUES = values();

    public static Tier byOrdinal(int o) {
        if (o < 0 || o >= VALUES.length) {
            throw new IllegalArgumentException("Tier ordinal out of range: " + o);
        }
        return VALUES[o];
    }
}
