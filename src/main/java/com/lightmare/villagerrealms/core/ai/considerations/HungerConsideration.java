package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.Curves;
import com.lightmare.villagerrealms.core.ai.EvalContext;

/**
 * "I am hungry." Score rises as hunger drops. Vitals.hunger is on a 0..20
 * scale (Minecraft food level convention; FRESH = 20). Below 8 the score
 * ramps up sharply, hitting 1.0 at 0.
 */
public final class HungerConsideration implements Consideration {

    public static final String ID = "hunger";

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 0L; }

    @Override
    public float score(EvalContext ctx) {
        float hunger = ctx.npc().vitals().hunger();
        float normalized = Curves.clamp01(hunger / 20f);
        return Curves.inverseQuadratic(normalized);
    }
}
