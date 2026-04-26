package com.lightmare.villagerrealms.core.ai;

/**
 * Pure function over (NPCRecord, world view) returning a utility score in
 * [0, 1]. Considerations are shared between full (Tier 0) and stripped
 * (Tier 1) evaluators — tuning one tunes both.
 *
 * Implementations must be referentially transparent given identical input
 * AND must not capture mutable state. Caching is handled externally by
 * {@link com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache}.
 */
public interface Consideration {

    /** Stable name; used as cache key. Should be unique across the registry. */
    String id();

    /**
     * True if this consideration is part of the minimal "essentials" set
     * the stripped (Tier 1) evaluator runs. Hunger, sleep, immediate danger
     * qualify; mood, social, long-tail considerations do not.
     */
    boolean essential();

    /**
     * Cache lifetime in ticks. 0 = recompute every call (for vital state
     * like current hunger). Larger values for considerations that read
     * derived/aggregated context (e.g. "is there food in the village").
     */
    long ttlTicks();

    /** Compute utility score in [0, 1]. */
    float score(EvalContext ctx);
}
