package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.record.NPCRecord;

/**
 * Read-only view passed to considerations and actions. Plain POJO — no
 * Minecraft imports, no live entity references — so the AI layer stays
 * testable in isolation from the game.
 *
 * Two clocks travel together:
 *   - {@code tick}: monotonic gameTime. Drives scheduling (cadence, stagger,
 *     catch-up). Always increases at one tick per server tick. Persists
 *     across restarts.
 *   - {@code dayTime}: vanilla day-time clock used by considerations that
 *     care about the in-game day-night cycle ({@code WorkTimeConsideration},
 *     {@code NightTimeConsideration}). May be set by {@code /time set} or
 *     frozen by {@code /gamerule doDaylightCycle false} — that's why it
 *     can't drive scheduling.
 *
 * villageView is nullable: at Tier 1/2 the AI may run without resolved
 * village context (the village shard might not be loaded). Considerations
 * that depend on village state must handle null and degrade gracefully.
 */
public record EvalContext(
        NPCRecord npc,
        long tick,
        long dayTime,
        VillageView villageView
) {
    public EvalContext {
        if (npc == null) throw new IllegalArgumentException("npc required");
    }

    /** Test/utility factory: dayTime defaults to {@code tick}. */
    public static EvalContext of(NPCRecord npc, long tick) {
        return new EvalContext(npc, tick, tick, null);
    }

    /** Factory with explicit dayTime, no village view. */
    public static EvalContext of(NPCRecord npc, long tick, long dayTime) {
        return new EvalContext(npc, tick, dayTime, null);
    }
}
