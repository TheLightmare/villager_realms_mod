package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.economy.Foods;
import com.lightmare.villagerrealms.core.economy.InventoryOps;
import com.lightmare.villagerrealms.core.economy.MarketPricing;
import com.lightmare.villagerrealms.core.economy.Substeps;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.role.Role;
import com.lightmare.villagerrealms.core.role.RoleRegistry;

import java.util.Optional;

/**
 * Tier 2 abstract simulator. Aggregate rules over an {@link NPCRecord} and
 * the village {@link Markets}; no IAUS, no entity, no actions.
 *
 * Per CLAUDE.md the abstract simulation is "plausible, not bit-identical."
 * This class collapses 100-tick cycles into a small set of deterministic
 * steps:
 *
 *   1. Hunger and energy drain linearly via {@link Substeps#drain}.
 *   2. If hunger drops below {@link #AUTO_EAT_THRESHOLD} and the NPC has
 *      food on hand, consume one unit (Substeps.consume).
 *   3. If hunger is below {@link #AUTO_BUY_THRESHOLD} and the NPC has no
 *      food but can afford the cheapest market food, buy + consume one
 *      unit.
 *   4. Workers run one production cycle per work-time cycle (matching the
 *      WorkTimeConsideration day-half gate so dormant workers don't out-
 *      produce active ones).
 *   5. Off work-time, energy restores via {@link Substeps#sleep} —
 *      dormant NPCs are assumed to be home. Both drain and restore
 *      use the same per-tick rates as the active runtime so transitions
 *      between tiers don't jolt vitals.
 *
 * Calls into {@link Substeps} keep the effects identical between Tier 0/1
 * (entity-driven) and Tier 2 (abstract) — same hunger restoration, same
 * market math, same provenance stamps. Selection logic differs by tier;
 * effects do not.
 *
 * Pure: never touches Minecraft, never persists, single-threaded.
 */
public final class AbstractTick {

    /** One abstract cycle equals 100 server ticks (CLAUDE.md). */
    public static final long CADENCE_TICKS = 100L;

    /** Hunger threshold below which the NPC eats from inventory. */
    public static final float AUTO_EAT_THRESHOLD = 14f;

    /** Hunger threshold below which the NPC will spend gold at the market. */
    public static final float AUTO_BUY_THRESHOLD = 10f;

    /** Vanilla Minecraft day length; mirrors WorkTimeConsideration. */
    public static final long DAY_TICKS = 24000L;

    /**
     * Cap on cycles applied in a single advance() call. Bounds runaway
     * catch-up after a long shutdown — CLAUDE.md says abstract sim is
     * plausible, not bit-identical, so collapsing >24h of dormancy is fine.
     */
    public static final int MAX_CYCLES_PER_CALL = 240;

    private AbstractTick() {}

    /**
     * Advance the record from {@code fromTick} up to {@code toTick} in
     * 100-tick increments. Returns the same instance if no cycles applied.
     */
    public static NPCRecord advance(NPCRecord rec, long fromTick, long toTick, Markets markets) {
        if (rec == null) return null;
        if (toTick <= fromTick) return rec;

        long elapsed = toTick - fromTick;
        long cycles = elapsed / CADENCE_TICKS;
        if (cycles <= 0L) return rec;
        if (cycles > MAX_CYCLES_PER_CALL) cycles = MAX_CYCLES_PER_CALL;

        long simTick = fromTick;
        for (long i = 0; i < cycles; i++) {
            simTick += CADENCE_TICKS;
            rec = applyCycle(rec, simTick, markets);
        }
        return rec;
    }

    /**
     * Apply one 100-tick cycle. Public for tests; production calls go
     * through {@link #advance}.
     */
    public static NPCRecord applyCycle(NPCRecord rec, long simTick, Markets markets) {
        rec = Substeps.drain(rec, CADENCE_TICKS);
        rec = maybeFeed(rec, simTick, markets);
        if (isWorkTime(simTick, rec)) {
            rec = maybeWork(rec, markets, simTick);
        } else {
            rec = Substeps.sleep(rec, CADENCE_TICKS);
        }
        return rec;
    }

    private static NPCRecord maybeFeed(NPCRecord rec, long simTick, Markets markets) {
        float hunger = rec.vitals().hunger();
        if (hunger >= AUTO_EAT_THRESHOLD) return rec;

        String inventoryFood = InventoryOps.firstMatching(rec.inventory(), Foods::isFood);
        if (inventoryFood != null) {
            return Substeps.consume(rec, inventoryFood, simTick);
        }

        if (hunger >= AUTO_BUY_THRESHOLD) return rec;

        String marketFood = cheapestAffordableFood(rec, markets);
        if (marketFood == null) return rec;

        NPCRecord bought = Substeps.buy(rec, marketFood, markets, simTick);
        if (bought == rec) return rec;
        return Substeps.consume(bought, marketFood, simTick);
    }

    private static NPCRecord maybeWork(NPCRecord rec, Markets markets, long simTick) {
        Role role = RoleRegistry.getOrLaborer(rec.role().roleId());
        if (!role.worker()) return rec;
        return Substeps.work(rec, markets, simTick);
    }

    private static boolean isWorkTime(long simTick, NPCRecord rec) {
        long offset = rec.role() == null ? 0L : rec.role().scheduleOffsetTicks();
        long timeOfDay = Math.floorMod(simTick + offset, DAY_TICKS);
        return timeOfDay < 12000L;
    }

    private static String cheapestAffordableFood(NPCRecord rec, Markets markets) {
        String villageId = rec.location().homeVillageId();
        if (villageId == null || villageId.isEmpty()) return null;
        Optional<VillageMarket> opt = markets.get(villageId);
        if (opt.isEmpty()) return null;
        VillageMarket m = opt.get();
        long gold = rec.economy().gold();

        String best = null;
        long bestPrice = Long.MAX_VALUE;
        for (var e : m.stockpile().entrySet()) {
            String id = e.getKey();
            if (e.getValue() <= 0) continue;
            if (!Foods.isFood(id)) continue;
            long p = MarketPricing.currentPrice(m, id);
            if (p > gold) continue;
            if (p < bestPrice) {
                bestPrice = p;
                best = id;
            }
        }
        return best;
    }
}
