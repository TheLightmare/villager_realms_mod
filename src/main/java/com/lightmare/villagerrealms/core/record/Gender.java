package com.lightmare.villagerrealms.core.record;

public enum Gender {
    MALE,
    FEMALE,
    OTHER;

    private static final Gender[] VALUES = values();

    public static Gender byOrdinal(int o) {
        if (o < 0 || o >= VALUES.length) {
            throw new IllegalArgumentException("Gender ordinal out of range: " + o);
        }
        return VALUES[o];
    }
}
