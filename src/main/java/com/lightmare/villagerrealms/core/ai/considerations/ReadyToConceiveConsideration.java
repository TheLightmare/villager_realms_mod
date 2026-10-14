package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.birth.Readiness;

/**
 * "I could have a child now." A 0/1 gate over {@link Readiness} — adult,
 * fed, rested, not already expecting, and the village has a free bed and
 * food. Not essential: Tier 1 never conceives via IAUS (the abstract sweep
 * covers it). Cached a few seconds; bed and food state move slowly.
 */
public final class ReadyToConceiveConsideration implements Consideration {

    public static final String ID = "ready_to_conceive";

    @Override public String id() { return ID; }
    @Override public boolean essential() { return false; }
    @Override public long ttlTicks() { return 100L; }

    @Override
    public float score(EvalContext ctx) {
        return Readiness.score(ctx.npc(), ctx.villageView(), ctx.tick());
    }
}
