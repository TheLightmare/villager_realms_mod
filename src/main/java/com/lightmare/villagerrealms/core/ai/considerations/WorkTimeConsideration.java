package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;

/**
 * "It is currently working hours, and I have a workplace." NPCs without a
 * workplace ref score 0 here. Time of day uses Minecraft's 24000-tick day
 * (tick % 24000); work window is dawn through dusk (0..12000), with a
 * narrower 0.5..1.0 ramp in the middle to prefer mid-day.
 */
public final class WorkTimeConsideration implements Consideration {

    public static final String ID = "work_time";

    /** Vanilla day length in ticks. */
    public static final long DAY_TICKS = 24000L;

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 200L; }

    @Override
    public float score(EvalContext ctx) {
        var role = ctx.npc().role();
        if (role == null || role.workplaceRef() == null || role.workplaceRef().isEmpty()) {
            return 0f;
        }

        long offset = role.scheduleOffsetTicks();
        long timeOfDay = Math.floorMod(ctx.dayTime() + offset, DAY_TICKS);

        if (timeOfDay >= 12000L) return 0f;

        float midDistance = Math.abs(timeOfDay - 6000L) / 6000f;
        return 1f - 0.5f * midDistance;
    }
}
