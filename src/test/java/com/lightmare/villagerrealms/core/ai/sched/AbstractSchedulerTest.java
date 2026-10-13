package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.economy.MapMarkets;
import com.lightmare.villagerrealms.core.economy.Substeps;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scheduler-level behaviour of {@link AbstractScheduler}: budget overflow and
 * the live → dormant hand-off. Per-cycle rules live in {@link AbstractTickTest}.
 */
class AbstractSchedulerTest {

    /** A UUID whose {@code msb ^ lsb} is zero lands in stagger bucket 0. */
    private static UUID bucketZeroUuid(long k) {
        return new UUID(k, k);
    }

    private static NPCRecord dormant(UUID id, float hunger) {
        NPCRecord r = Fixtures.npc(id, "v", 0, 0);
        Location l = r.location();
        Location nl = new Location(l.homeVillageId(), l.x(), l.y(), l.z(), l.dimension(), Tier.DORMANT);
        Vitals v = r.vitals();
        return new NPCRecord(
                r.dataVersion(), r.identity(), nl,
                new Vitals(v.health(), hunger, v.energy(), v.mood()),
                NPCInventory.EMPTY, r.economy(), r.role(), r.factionId(),
                r.relationships(), r.memory(), r.action());
    }

    private static MapMarkets emptyMarket() {
        MapMarkets m = new MapMarkets();
        m.put("v", VillageMarket.EMPTY);
        return m;
    }

    /** A tick that is a multiple of the cadence and in the sleep half of the day. */
    private static final long SLEEP_SLOT0 = 16_800L;

    @Test
    void overflowDefersBeyondBudgetAndDrainsOnLaterTicks() {
        Map<UUID, NPCRecord> world = new HashMap<>();
        for (long k = 1; k <= 5; k++) {
            UUID id = bucketZeroUuid(k);
            world.put(id, dormant(id, 20f));
        }
        AbstractScheduler sched = new AbstractScheduler(1, emptyMarket());

        // All five collide in bucket 0. Budget is 1 → one runs, four defer.
        SchedulerStats t0 = sched.tick(0L, new ArrayList<>(world.values()),
                world::get, r -> { world.put(r.identity().uuid(), r); return r; });
        assertEquals(1, t0.evaluated());
        assertEquals(4, t0.deferredAdded());
        assertEquals(0, t0.deferredDrained());
        assertEquals(4, t0.overflowSize());
        assertEquals(4, sched.overflowSize());

        // Each following tick drains exactly one, regardless of bucket.
        int totalEvaluated = t0.evaluated();
        for (long t = 1; t <= 4; t++) {
            SchedulerStats s = sched.tick(t, new ArrayList<>(world.values()),
                    world::get, r -> { world.put(r.identity().uuid(), r); return r; });
            assertEquals(1, s.evaluated(), "tick " + t);
            assertEquals(1, s.deferredDrained(), "tick " + t);
            assertEquals(0, s.deferredAdded(), "tick " + t);
            assertEquals(4 - (int) t, s.overflowSize(), "tick " + t);
            totalEvaluated += s.evaluated();
        }
        assertEquals(5, totalEvaluated, "every NPC evaluated exactly once");
        assertEquals(0, sched.overflowSize());

        // Remainder of the cadence window: nothing left to do.
        for (long t = 5; t < AbstractTick.CADENCE_TICKS; t++) {
            SchedulerStats s = sched.tick(t, new ArrayList<>(world.values()),
                    world::get, r -> { world.put(r.identity().uuid(), r); return r; });
            assertEquals(0, s.evaluated(), "tick " + t);
        }
    }

    @Test
    void overflowDoesNotDoubleQueueAnNpcAlreadyDeferred() {
        Map<UUID, NPCRecord> world = new HashMap<>();
        for (long k = 1; k <= 3; k++) {
            UUID id = bucketZeroUuid(k);
            world.put(id, dormant(id, 20f));
        }
        AbstractScheduler sched = new AbstractScheduler(1, emptyMarket());

        sched.tick(0L, new ArrayList<>(world.values()),
                world::get, r -> { world.put(r.identity().uuid(), r); return r; });
        assertEquals(2, sched.overflowSize());

        // Same bucket fires again 100 ticks later while two (B, C) are still
        // queued. Budget 1 drains B. The bucket pass then sees A (re-eligible,
        // queued), B (just drained, re-eligible, queued — a harmless no-op next
        // tick since zero cycles elapsed) and C (still queued → must NOT be
        // added a second time). Without the de-dup guard the queue would be 4.
        SchedulerStats s = sched.tick(AbstractTick.CADENCE_TICKS, new ArrayList<>(world.values()),
                world::get, r -> { world.put(r.identity().uuid(), r); return r; });
        assertEquals(1, s.deferredDrained());
        assertEquals(2, s.deferredAdded(), "A and B queued; C skipped as already queued");
        assertEquals(3, s.overflowSize());
        assertEquals(3, sched.overflowSize());
    }

    @Test
    void markCurrentPreventsReplayOfLivePeriod() {
        UUID id = bucketZeroUuid(7L);
        NPCRecord rec = dormant(id, 20f);
        MapMarkets markets = emptyMarket();
        AbstractScheduler sched = new AbstractScheduler(64, markets);

        // Establish dormant history: first sight at 0, then 50 cycles of
        // abstract sim up to tick 5000.
        assertSame(rec, sched.catchUp(rec, 0L));
        NPCRecord afterDormant = sched.catchUp(rec, AbstractTick.CADENCE_TICKS * 50L);
        assertNotSame(rec, afterDormant);

        // The NPC is promoted and driven live for a long time. The live
        // runtime applies its own drain; the record we hold is whatever the
        // live sim left behind. Demotion stamps "simulated up to now".
        sched.markCurrent(id, SLEEP_SLOT0);
        NPCRecord liveResult = dormant(id, 20f);

        // Exactly three cadences after the stamp: exactly three cycles, not
        // the ~118 that elapsed since the last dormant tick.
        NPCRecord after = sched.catchUp(liveResult, SLEEP_SLOT0 + AbstractTick.CADENCE_TICKS * 3L);
        float drainPerCycle = Substeps.HUNGER_DRAIN_PER_TICK * AbstractTick.CADENCE_TICKS;
        assertEquals(20f - 3f * drainPerCycle, after.vitals().hunger(), 0.01f);

        // Less than one further cadence: nothing to apply.
        assertSame(after, sched.catchUp(after, SLEEP_SLOT0 + AbstractTick.CADENCE_TICKS * 3L + 50L));
    }

    @Test
    void schedulerTickAfterMarkCurrentAppliesOnlyNewCycles() {
        UUID id = bucketZeroUuid(11L);
        MapMarkets markets = emptyMarket();
        AbstractScheduler sched = new AbstractScheduler(64, markets);

        // Dormant history ending at tick 5000.
        NPCRecord original = dormant(id, 20f);
        sched.catchUp(original, 0L);
        sched.catchUp(original, AbstractTick.CADENCE_TICKS * 50L);

        // Long live period, then demotion at SLEEP_SLOT0 (bucket 0 slot).
        sched.markCurrent(id, SLEEP_SLOT0);
        Map<UUID, NPCRecord> world = new HashMap<>();
        world.put(id, dormant(id, 20f));
        List<NPCRecord> commits = new ArrayList<>();

        // Bucket fires on the demotion tick itself: zero elapsed → no commit.
        SchedulerStats s0 = sched.tick(SLEEP_SLOT0, new ArrayList<>(world.values()),
                world::get, r -> { commits.add(r); world.put(r.identity().uuid(), r); return r; });
        assertEquals(1, s0.evaluated());
        assertTrue(commits.isEmpty(), "no cycles elapsed since the stamp → nothing committed");

        // One cadence later: exactly one cycle.
        sched.tick(SLEEP_SLOT0 + AbstractTick.CADENCE_TICKS, new ArrayList<>(world.values()),
                world::get, r -> { commits.add(r); world.put(r.identity().uuid(), r); return r; });
        assertEquals(1, commits.size());
        float drainPerCycle = Substeps.HUNGER_DRAIN_PER_TICK * AbstractTick.CADENCE_TICKS;
        assertEquals(20f - drainPerCycle, commits.get(0).vitals().hunger(), 0.01f);
    }
}
