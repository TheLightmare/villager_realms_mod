package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.Curves;
import com.lightmare.villagerrealms.core.ai.EvalContext;

/**
 * "I am tired." Score rises as energy drops. Vitals.energy is on a 0..1
 * scale (1 = fully rested). Quadratic curve so a slightly tired NPC does
 * not preempt work, but a very tired one will.
 */
public final class SleepConsideration implements Consideration {

    public static final String ID = "sleep";

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 0L; }

    @Override
    public float score(EvalContext ctx) {
        float energy = ctx.npc().vitals().energy();
        return Curves.inverseQuadratic(Curves.clamp01(energy));
    }
}
