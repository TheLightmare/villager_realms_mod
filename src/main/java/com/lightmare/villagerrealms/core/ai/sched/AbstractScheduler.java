package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Tier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Tier 2 (DORMANT) scheduler. Same shape as {@link AIScheduler} — staggered
 * UUID-hash buckets, per-tick budget, deferred overflow queue — but it
 * drives {@link AbstractTick} instead of an evaluator.
 *
 * Cadence is fixed at {@link AbstractTick#CADENCE_TICKS} (100 ticks per
 * CLAUDE.md). NPCs whose tier is anything other than DORMANT are skipped.
 *
 * State held here:
 *   - {@code overflow}/{@code overflowSet}: deferred work queue, identical
 *     pattern to AIScheduler.
 *   - {@code lastAbstractTick}: per-NPC bookkeeping of when each NPC was
 *     last abstract-simmed. In-memory only by design — a server restart
 *     loses at most one cycle of catch-up, which is acceptable under the
 *     "plausible, not bit-identical" rule. If restart-stable catch-up is
 *     ever required, persist this map (e.g. as the SimulationScheduler
 *     store CLAUDE.md outlines) instead of bumping the NPCRecord schema.
 *
 * Entry points:
 *   - {@link #tick}: drive the regular per-cycle pass.
 *   - {@link #catchUp}: external callers (e.g. the Tier 2 → Tier 0 promotion
 *     site) advance one NPC up to the current tick before re-projecting.
 */
public final class AbstractScheduler {

    @FunctionalInterface
    public interface NPCLookup {
        NPCRecord get(UUID id);
    }

    private final int budgetPerTick;
    private final Markets markets;
    private final Deque<UUID> overflow = new ArrayDeque<>();
    private final Set<UUID> overflowSet = new HashSet<>();
    private final Map<UUID, Long> lastAbstractTick = new HashMap<>();

    public AbstractScheduler(int budgetPerTick, Markets markets) {
        if (budgetPerTick <= 0) throw new IllegalArgumentException("budgetPerTick must be > 0");
        if (markets == null) throw new IllegalArgumentException("markets required");
        this.budgetPerTick = budgetPerTick;
        this.markets = markets;
    }

    public SchedulerStats tick(
            long currentTick,
            Iterable<NPCRecord> npcs,
            NPCLookup lookup,
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
            advance(rec, currentTick, commit);
            evaluated++;
            deferredDrained++;
            budget--;
        }

        long cadence = AbstractTick.CADENCE_TICKS;
        for (NPCRecord rec : npcs) {
            if (!isScheduleable(rec)) continue;
            UUID uuid = rec.identity().uuid();
            int bucket = (int) Math.floorMod(
                    uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits(),
                    cadence);
            int slot = (int) Math.floorMod(currentTick, cadence);
            if (bucket != slot) continue;
            if (overflowSet.contains(uuid)) continue;

            if (budget > 0) {
                advance(rec, currentTick, commit);
                evaluated++;
                budget--;
            } else {
                overflow.addLast(uuid);
                overflowSet.add(uuid);
                deferredAdded++;
            }
        }

        return new SchedulerStats(evaluated, deferredAdded, deferredDrained, overflow.size());
    }

    /**
     * Run any overdue abstract cycles for one NPC. Idempotent: if no time
     * has elapsed since the last abstract tick, returns the input record.
     * Updates {@code lastAbstractTick} so the regular scheduler does not
     * double-apply.
     *
     * Use from the Tier 2 → Tier 0 transition site (chunk-load /
     * promotion) to avoid projecting stale state onto a fresh entity.
     */
    public NPCRecord catchUp(NPCRecord rec, long currentTick) {
        if (rec == null) return null;
        UUID uuid = rec.identity().uuid();
        long from = lastAbstractTick.getOrDefault(uuid, currentTick);
        NPCRecord advanced = AbstractTick.advance(rec, from, currentTick, markets);
        lastAbstractTick.put(uuid, currentTick);
        return advanced;
    }

    /** Drop any cached state for an NPC — e.g. on remove() or full reset. */
    public void forget(UUID uuid) {
        lastAbstractTick.remove(uuid);
        if (overflowSet.remove(uuid)) overflow.remove(uuid);
    }

    public void reset() {
        overflow.clear();
        overflowSet.clear();
        lastAbstractTick.clear();
    }

    public int overflowSize() { return overflow.size(); }

    private void advance(NPCRecord rec, long currentTick, UnaryOperator<NPCRecord> commit) {
        UUID uuid = rec.identity().uuid();
        long from = lastAbstractTick.getOrDefault(uuid, currentTick);
        NPCRecord next = AbstractTick.advance(rec, from, currentTick, markets);
        lastAbstractTick.put(uuid, currentTick);
        if (next != null && next != rec) {
            commit.apply(next);
        }
    }

    private static boolean isScheduleable(NPCRecord rec) {
        return rec.location().tier() == Tier.DORMANT;
    }
}
