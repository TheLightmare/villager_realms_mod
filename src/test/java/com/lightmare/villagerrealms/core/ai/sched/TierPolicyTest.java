package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.record.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierPolicyTest {

    private static final double RADIUS = 96.0;
    private static final long COLD_AFTER = 72_000L;

    private static Tier resolve(boolean loaded, double nearestSq, long dormantTicks) {
        return TierPolicy.resolve(loaded, nearestSq, RADIUS, dormantTicks, COLD_AFTER);
    }

    @Test
    void loadedWithPlayerInsideRadiusIsActive() {
        double inside = (RADIUS - 1) * (RADIUS - 1);
        assertEquals(Tier.ACTIVE, resolve(true, inside, 0L));
    }

    @Test
    void atExactlyRadiusIsActive() {
        assertEquals(Tier.ACTIVE, resolve(true, RADIUS * RADIUS, 0L));
    }

    @Test
    void loadedWithPlayerOutsideRadiusIsNearby() {
        double outside = (RADIUS + 1) * (RADIUS + 1);
        assertEquals(Tier.NEARBY, resolve(true, outside, 0L));
    }

    @Test
    void loadedWithNoPlayerIsNearby() {
        // negative distance encodes "no player present"
        assertEquals(Tier.NEARBY, resolve(true, -1.0, 0L));
    }

    @Test
    void unloadedRecentlyIsDormant() {
        assertEquals(Tier.DORMANT, resolve(false, -1.0, 0L));
        assertEquals(Tier.DORMANT, resolve(false, -1.0, COLD_AFTER - 1));
    }

    @Test
    void unloadedLongEnoughIsCold() {
        assertEquals(Tier.COLD, resolve(false, -1.0, COLD_AFTER));
        assertEquals(Tier.COLD, resolve(false, -1.0, COLD_AFTER * 10));
    }

    @Test
    void proximityIgnoredWhenUnloaded() {
        // Even a tiny "distance" can't make an unloaded village active —
        // no entity exists when chunks are gone.
        assertEquals(Tier.DORMANT, resolve(false, 0.0, 0L));
    }
}
