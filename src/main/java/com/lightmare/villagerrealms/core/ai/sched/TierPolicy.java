package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.record.Tier;

/**
 * Pure tier-assignment policy. Decides a single {@link Tier} for a whole
 * village from its chunk-load state and player proximity, per CLAUDE.md:
 *
 *   "Every NPC exists at one of four tiers, determined by chunk-load state
 *    and player proximity to the NPC's home village (NPCs are tier-pinned to
 *    their village's loaded state, not their personal position — this keeps
 *    groups coherent)."
 *
 * Because the decision is per-village, every NPC of a village receives the
 * same tier; groups stay coherent by construction.
 *
 * Mapping:
 *   - village chunks loaded + a player within {@code activeRadius} -> ACTIVE
 *   - village chunks loaded, no player that close                  -> NEARBY
 *   - village chunks unloaded, recently                            -> DORMANT
 *   - village chunks unloaded for >= coldAfterTicks                -> COLD
 *
 * COLD vs DORMANT: DORMANT villages are abstract-simulated every cadence
 * (they "live" offline); COLD villages are frozen and only caught up when
 * next queried (chunk load / promotion). The catch-up that wakes a COLD
 * village is the same {@code AbstractScheduler.catchUp} used for DORMANT,
 * capped at {@code AbstractTick.MAX_CYCLES_PER_CALL} — plausible, not
 * bit-identical.
 *
 * No Minecraft types, no state: trivially unit-testable.
 */
public final class TierPolicy {

    private TierPolicy() {}

    /**
     * @param loaded              whether the village's representative chunk is loaded
     * @param nearestPlayerDistSq squared distance (blocks) to the nearest player in
     *                            the village's dimension, or a negative value when no
     *                            player is present / not applicable
     * @param activeRadius        block radius within which a loaded village is ACTIVE
     * @param dormantTicks        how long the village has been continuously unloaded
     *                            (0 when loaded)
     * @param coldAfterTicks      dormancy duration after which a village goes COLD
     */
    public static Tier resolve(boolean loaded,
                               double nearestPlayerDistSq,
                               double activeRadius,
                               long dormantTicks,
                               long coldAfterTicks) {
        if (loaded) {
            if (nearestPlayerDistSq >= 0.0 && nearestPlayerDistSq <= activeRadius * activeRadius) {
                return Tier.ACTIVE;
            }
            return Tier.NEARBY;
        }
        return dormantTicks >= coldAfterTicks ? Tier.COLD : Tier.DORMANT;
    }
}
