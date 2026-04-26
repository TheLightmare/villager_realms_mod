package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.ai.Decision;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.Evaluator;
import com.lightmare.villagerrealms.core.ai.VillageView;
import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Tier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Round-robin AI scheduler with per-tick budget. Single-threaded; runs on
 * the server tick.
 *
 * Cadence: an NPC's UUID hash assigns it a bucket modulo the cadence; the
 * NPC is due to evaluate when {@code tick % cadence == bucket}. This
 * smears load uniformly across ticks without any extra bookkeeping.
 *
 * Tier 0 (ACTIVE) uses the full evaluator at active cadence (default 20
 * ticks). Tier 1 (NEARBY) uses the stripped evaluator at a longer cadence
 * (default 80 ticks). Tier 2 (DORMANT) and Tier 3 (COLD) are not handled
 * here — abstract simulation lives elsewhere.
 *
 * Budget enforcement: at most {@code budgetPerTick} evaluations per tick.
 * Overflow is appended to a deferred queue that drains first on subsequent
 * ticks. The queue carries UUIDs (not records) so a record updated between
 * scheduling and draining is observed at its current state.
 *
 * Village views: an optional {@link VillageViewResolver} supplies a
 * {@link VillageView} for the NPC's home village. v1 needs this so
 * BuyFoodAction can read market state. The resolver may return null when
 * the village is unloaded; considerations must handle null views.
 */
public final class AIScheduler {

    /**
     * Resolves an NPC by UUID at drain time. Returning null means the NPC
     * is no longer scheduleable (removed, despawned, demoted to DORMANT)
     * and the deferred slot is dropped.
     */
    @FunctionalInterface
    public interface NPCLookup {
        NPCRecord get(UUID id);
    }

    /**
     * Side effect that commits an evaluator decision. Returning a new
     * record causes it to be {@link com.lightmare.villagerrealms.core.persist.store.NPCRegistry#put}'d
     * by the caller; returning the input record is a no-op signal.
     */
    @FunctionalInterface
    public interface DecisionApplier {
        NPCRecord apply(NPCRecord npc, Decision decision, long tick);
    }

    /**
     * Resolves a {@link VillageView} for an NPC's home village. Return null
     * when no view is available (cold village, missing record).
     */
    @FunctionalInterface
    public interface VillageViewResolver extends Function<NPCRecord, VillageView> {}

    private static final VillageViewResolver NO_VIEW = npc -> null;

    private final Evaluator full;
    private final Evaluator stripped;
    private final ConsiderationCache cache;
    private final int budgetPerTick;
    private final int activeCadence;
    private final int nearbyCadence;
    private final VillageViewResolver views;

    private final Deque<UUID> overflow = new ArrayDeque<>();
    private final Set<UUID> overflowSet = new HashSet<>();

    public AIScheduler(Evaluator full, Evaluator stripped, ConsiderationCache cache,
                       int budgetPerTick, int activeCadence, int nearbyCadence) {
        this(full, stripped, cache, budgetPerTick, activeCadence, nearbyCadence, NO_VIEW);
    }

    public AIScheduler(Evaluator full, Evaluator stripped, ConsiderationCache cache,
                       int budgetPerTick, int activeCadence, int nearbyCadence,
                       VillageViewResolver views) {
        if (full == null || stripped == null) {
            throw new IllegalArgumentException("evaluators required");
        }
        if (cache == null) throw new IllegalArgumentException("cache required");
        if (budgetPerTick <= 0) throw new IllegalArgumentException("budgetPerTick must be > 0");
        if (activeCadence <= 0 || nearbyCadence <= 0) {
            throw new IllegalArgumentException("cadence must be > 0");
        }
        this.full = full;
        this.stripped = stripped;
        this.cache = cache;
        this.budgetPerTick = budgetPerTick;
        this.activeCadence = activeCadence;
        this.nearbyCadence = nearbyCadence;
        this.views = views == null ? NO_VIEW : views;
    }

    public ConsiderationCache cache() { return cache; }

    public SchedulerStats tick(
            long currentTick,
            long dayTime,
            Iterable<NPCRecord> npcs,
            NPCLookup lookup,
            DecisionApplier applier,
            UnaryOperator<NPCRecord> commit) {

        int budget = budgetPerTick;
        int evaluated = 0;
        int deferredAdded = 0;
        int deferredDrained = 0;

        while (budget > 0 && !overflow.isEmpty()) {
            UUID id = overflow.pollFirst();
            overflowSet.remove(id);
            NPCRecord rec = lookup.get(id);
            if (rec == null) continue;
            if (!isScheduleable(rec)) continue;
            evaluateAndCommit(rec, currentTick, dayTime, applier, commit);
            evaluated++;
            deferredDrained++;
            budget--;
        }

        for (NPCRecord rec : npcs) {
            if (!isScheduleable(rec)) continue;
            int cadence = rec.location().tier() == Tier.ACTIVE ? activeCadence : nearbyCadence;
            int bucket = (int) (Math.floorMod(rec.identity().uuid().getMostSignificantBits()
                    ^ rec.identity().uuid().getLeastSignificantBits(), (long) cadence));
            int slot = (int) Math.floorMod(currentTick, (long) cadence);
            if (bucket != slot) continue;

            if (overflowSet.contains(rec.identity().uuid())) continue;

            if (budget > 0) {
                evaluateAndCommit(rec, currentTick, dayTime, applier, commit);
                evaluated++;
                budget--;
            } else {
                overflow.addLast(rec.identity().uuid());
                overflowSet.add(rec.identity().uuid());
                deferredAdded++;
            }
        }

        return new SchedulerStats(evaluated, deferredAdded, deferredDrained, overflow.size());
    }

    private void evaluateAndCommit(NPCRecord rec, long tick, long dayTime,
                                   DecisionApplier applier, UnaryOperator<NPCRecord> commit) {
        Tier tier = rec.location().tier();
        Evaluator e = tier == Tier.ACTIVE ? full : stripped;
        VillageView view = views.apply(rec);
        EvalContext ctx = new EvalContext(rec, tick, dayTime, view);
        Decision d = e.evaluate(ctx, cache);
        NPCRecord updated = applier.apply(rec, d, tick);
        if (updated != null && updated != rec) {
            commit.apply(updated);
        }
    }

    private static boolean isScheduleable(NPCRecord rec) {
        Tier t = rec.location().tier();
        return t == Tier.ACTIVE || t == Tier.NEARBY;
    }

    public int overflowSize() { return overflow.size(); }
}
