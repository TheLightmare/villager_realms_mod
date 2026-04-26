package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.ai.Evaluator;
import com.lightmare.villagerrealms.core.ai.Evaluators;
import com.lightmare.villagerrealms.core.ai.RecordVillageView;
import com.lightmare.villagerrealms.core.ai.VillageView;
import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;
import com.lightmare.villagerrealms.core.ai.sched.AIScheduler;
import com.lightmare.villagerrealms.core.ai.sched.AbstractScheduler;
import com.lightmare.villagerrealms.core.ai.sched.Markets;
import com.lightmare.villagerrealms.core.ai.sched.SchedulerStats;
import com.lightmare.villagerrealms.core.ai.sched.StepExecutor;
import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.persist.store.VillageStore;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-server-instance owner of the active AI scheduler, the Tier 2
 * abstract scheduler, the consideration cache, the action-selection
 * applier, and the {@link Markets} adapter.
 *
 * Lifecycle: start() at ServerStartingEvent, stop() at ServerStoppedEvent.
 * Single-threaded — tick() must run on the server thread.
 *
 * Effects (consume / work / buy / etc.) are NOT executed from this class.
 * Tier 0/1 effects are entity-bound and live in
 * {@link ActiveStepRuntime}. Tier 2 effects come from the
 * {@link AbstractScheduler} via {@link com.lightmare.villagerrealms.core.ai.sched.AbstractTick}
 * — same {@code Substeps} as the active runtime, just selected by aggregate
 * rules instead of IAUS.
 */
public final class AIService {

    private static final Logger LOG = LoggerFactory.getLogger(AIService.class);
    private static volatile AIService current;

    /** Default budget per tick. Tunable; sized for thousands of NPCs. */
    public static final int DEFAULT_BUDGET = 64;
    /**
     * Tier 2 budget. Smaller than the active budget — abstract cycles are
     * cheaper individually, but a 100-tick cadence means the population is
     * smeared across 100 buckets so per-tick demand stays modest.
     */
    public static final int DEFAULT_ABSTRACT_BUDGET = 32;
    public static final int ACTIVE_CADENCE = 20;
    public static final int NEARBY_CADENCE = 80;
    public static final int CACHE_PRUNE_INTERVAL_TICKS = 6000;

    private final AIScheduler scheduler;
    private final AbstractScheduler abstractScheduler;
    private final ConsiderationCache cache;
    private final StepExecutor applier;
    private final Markets markets;
    private final AIScheduler.VillageViewResolver views;

    private SchedulerStats lastStats = new SchedulerStats(0, 0, 0, 0);
    private SchedulerStats lastAbstractStats = new SchedulerStats(0, 0, 0, 0);
    private long lastPruneTick = 0L;

    private AIService(int budgetPerTick, int abstractBudgetPerTick, VillageStore villages) {
        this.cache = new ConsiderationCache();
        Evaluator full = Evaluators.full();
        Evaluator stripped = Evaluators.stripped();
        this.markets = new VillageStoreMarkets(villages);
        this.applier = new StepExecutor();

        this.views = npc -> {
            String vid = npc.location().homeVillageId();
            if (vid == null || vid.isEmpty()) return null;
            VillageRecord rec = villages.get(vid).orElse(null);
            return rec == null ? null : new RecordVillageView(rec);
        };

        this.scheduler = new AIScheduler(full, stripped, cache,
                budgetPerTick, ACTIVE_CADENCE, NEARBY_CADENCE, views);
        this.abstractScheduler = new AbstractScheduler(abstractBudgetPerTick, markets);
    }

    public AIScheduler scheduler() { return scheduler; }
    public AbstractScheduler abstractScheduler() { return abstractScheduler; }
    public ConsiderationCache cache() { return cache; }
    public StepExecutor applier() { return applier; }
    public Markets markets() { return markets; }
    public SchedulerStats lastStats() { return lastStats; }
    public SchedulerStats lastAbstractStats() { return lastAbstractStats; }

    /**
     * Run any overdue abstract cycles for this NPC up to {@code currentTick}.
     * Called from the Tier 2 → Tier 0 transition site (entity join / chunk
     * load) so a fresh entity is never projected from a stale record. The
     * caller is responsible for committing the returned record.
     */
    public NPCRecord catchUp(NPCRecord npc, long currentTick) {
        return abstractScheduler.catchUp(npc, currentTick);
    }

    /** Resolve the village view for an NPC; null if the home village isn't loaded. */
    public VillageView viewFor(NPCRecord npc) {
        return views.apply(npc);
    }

    public static AIService get() {
        AIService c = current;
        if (c == null) throw new IllegalStateException("AIService not started");
        return c;
    }

    public static AIService getOrNull() { return current; }

    static synchronized void start(VillageStore villages) {
        if (current != null) {
            LOG.warn("AIService.start called but instance already exists; replacing");
            current = null;
        }
        current = new AIService(DEFAULT_BUDGET, DEFAULT_ABSTRACT_BUDGET, villages);
        LOG.info("AIService started: budget={}, abstractBudget={}, activeCadence={}, nearbyCadence={}",
                DEFAULT_BUDGET, DEFAULT_ABSTRACT_BUDGET, ACTIVE_CADENCE, NEARBY_CADENCE);
    }

    static synchronized void stop() {
        AIService svc = current;
        if (svc == null) return;
        svc.cache.clear();
        svc.abstractScheduler.reset();
        current = null;
        LOG.info("AIService stopped");
    }

    /**
     * Run one scheduler tick. {@code currentTick} is the world's monotonic
     * gameTime (drives cadence, stagger, catch-up); {@code dayTime} is
     * vanilla bed-timing for time-of-day considerations. Caller must hold
     * the server thread.
     */
    public void tick(long currentTick, long dayTime, NPCRegistry registry) {
        lastStats = scheduler.tick(
                currentTick,
                dayTime,
                registry.all(),
                id -> registry.get(id).orElse(null),
                applier,
                rec -> { registry.put(rec); return rec; });

        lastAbstractStats = abstractScheduler.tick(
                currentTick,
                registry.all(),
                id -> registry.get(id).orElse(null),
                rec -> { registry.put(rec); return rec; });

        if (currentTick - lastPruneTick >= CACHE_PRUNE_INTERVAL_TICKS) {
            int dropped = cache.pruneStale(currentTick);
            if (dropped > 0) {
                LOG.debug("ConsiderationCache pruned {} stale entries at tick {}", dropped, currentTick);
            }
            lastPruneTick = currentTick;
        }
    }
}
