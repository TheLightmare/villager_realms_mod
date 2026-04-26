package com.lightmare.villagerrealms.core.record;

public record Vitals(
        float health,
        float hunger,
        float energy,
        float mood
) {
    public static final Vitals FRESH = new Vitals(20.0f, 20.0f, 1.0f, 0.5f);
}
