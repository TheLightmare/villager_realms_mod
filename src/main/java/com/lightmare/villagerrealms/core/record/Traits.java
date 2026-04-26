package com.lightmare.villagerrealms.core.record;

public record Traits(
        float aggression,
        float ambition,
        float diligence,
        float empathy,
        float gregariousness,
        float thrift
) {
    public static final Traits NEUTRAL = new Traits(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
}
