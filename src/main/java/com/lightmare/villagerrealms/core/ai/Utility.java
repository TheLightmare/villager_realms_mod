package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;

import java.util.List;

/**
 * IAUS scoring math. The core rule: action utility = product of
 * consideration scores, then compensated by Dave Mark's correction so
 * actions with more considerations are not punished by raw multiplication.
 *
 * Any zero-scoring consideration produces a zero utility — the killing
 * floor. This is the cheapest way to express "action X is gated by
 * predicate P".
 */
public final class Utility {

    private Utility() {}

    /**
     * Score one action against the given context, using the cache for
     * considerations whose ttlTicks &gt; 0.
     */
    public static float scoreAction(Action action, EvalContext ctx, ConsiderationCache cache) {
        List<Consideration> cs = action.considerations();
        if (cs.isEmpty()) return 0f;

        float product = 1f;
        for (Consideration c : cs) {
            float s = cache != null
                    ? cache.scoreCached(c, ctx)
                    : Curves.clamp01(c.score(ctx));
            if (s <= 0f) return 0f;
            product *= s;
        }

        int n = cs.size();
        float modFactor = n <= 1 ? 0f : 1f - 1f / n;
        float compensated = product + (1f - product) * modFactor * product;

        return Curves.clamp01(compensated * action.baseWeight());
    }
}
