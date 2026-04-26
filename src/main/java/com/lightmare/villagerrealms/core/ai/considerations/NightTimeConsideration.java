package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;

/**
 * "It's night out." Mirrors vanilla bed-usability timing: returns 1 when
 * the world clock is in the second half of the day (12000..23999), 0
 * during the daytime half.
 *
 * Used to gate SleepAction so NPCs only seek their beds at night, matching
 * vanilla villager behavior. Unlike {@link WorkTimeConsideration} this does
 * NOT apply per-NPC schedule offsets — bedtime is a world-wide signal,
 * not a personal one.
 */
public final class NightTimeConsideration implements Consideration {

    public static final String ID = "night_time";

    /** Vanilla day length in ticks; matches WorkTimeConsideration. */
    public static final long DAY_TICKS = 24000L;

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 200L; }

    @Override
    public float score(EvalContext ctx) {
        long timeOfDay = Math.floorMod(ctx.dayTime(), DAY_TICKS);
        return timeOfDay >= 12000L ? 1f : 0f;
    }
}
