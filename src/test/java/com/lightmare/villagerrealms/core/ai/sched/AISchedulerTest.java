package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.Evaluators;
import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Tier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AISchedulerTest {

    private static NPCRecord withTier(NPCRecord r, Tier t) {
        Location l = r.location();
        Location nl = new Location(l.homeVillageId(), l.x(), l.y(), l.z(), l.dimension(), t);
        return new NPCRecord(
                r.dataVersion(), r.identity(), nl,
                r.vitals(), r.inventory(), r.economy(), r.role(),
                r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static AIScheduler scheduler(int budget) {
        return new AIScheduler(
                Evaluators.full(), Evaluators.stripped(),
                new ConsiderationCache(),
                budget, 20, 80);
    }

    private static class World {
        final Map<UUID, NPCRecord> npcs = new HashMap<>();
        void put(NPCRecord r) { npcs.put(r.identity().uuid(), r); }
        AIScheduler.NPCLookup lookup() { return id -> npcs.get(id); }
        List<NPCRecord> all() { return new ArrayList<>(npcs.values()); }
    }

    @Test
    void evaluatesActiveNpcAndCommitsAction() {
        World w = new World();
        var hungry = withTier(Fixtures.npc(UUID.randomUUID(), "v", 0, 0), Tier.ACTIVE);
        // Make the NPC clearly hungry so EatAction wins.
        var v = hungry.vitals();
        hungry = new NPCRecord(
                hungry.dataVersion(), hungry.identity(), hungry.location(),
                new com.lightmare.villagerrealms.core.record.Vitals(v.health(), 1f, 1f, v.mood()),
                hungry.inventory(), hungry.economy(), hungry.role(),
                hungry.factionId(), hungry.relationships(), hungry.memory(), hungry.action());
        w.put(hungry);

        var sch = scheduler(8);

        UUID uid = hungry.identity().uuid();
        long bucket = Math.floorMod(uid.getMostSignificantBits() ^ uid.getLeastSignificantBits(), 20L);

        SchedulerStats stats = sch.tick(bucket, bucket, w.all(), w.lookup(),
                new ActionStateApplier(), r -> { w.put(r); return r; });

        assertEquals(1, stats.evaluated());
        // Note: bucket is used as dayTime here too; specific tests can pass
        // a distinct dayTime when they care about night/day gating.
        assertEquals("eat", w.npcs.get(uid).action().actionId());
    }

    @Test
    void coldNpcsAreSkipped() {
        World w = new World();
        for (int i = 0; i < 50; i++) {
            w.put(withTier(Fixtures.npc(UUID.randomUUID(), "v", i, 0), Tier.COLD));
        }
        var sch = scheduler(64);
        SchedulerStats total = new SchedulerStats(0, 0, 0, 0);
        for (long t = 0; t < 80; t++) {
            SchedulerStats s = sch.tick(t, t, w.all(), w.lookup(),
                    new ActionStateApplier(), r -> { w.put(r); return r; });
            total = new SchedulerStats(
                    total.evaluated() + s.evaluated(),
                    total.deferredAdded() + s.deferredAdded(),
                    total.deferredDrained() + s.deferredDrained(),
                    s.overflowSize());
        }
        assertEquals(0, total.evaluated());
    }

    @Test
    void budgetIsEnforcedAndOverflowDrains() {
        World w = new World();
        for (int i = 0; i < 200; i++) {
            w.put(withTier(Fixtures.npc(UUID.randomUUID(), "v", i, 0), Tier.ACTIVE));
        }
        var sch = scheduler(4);

        int totalEvaluated = 0;
        int maxOverflow = 0;
        for (long t = 0; t < 400; t++) {
            SchedulerStats s = sch.tick(t, t, w.all(), w.lookup(),
                    new ActionStateApplier(), r -> { w.put(r); return r; });
            assertTrue(s.evaluated() <= 4, "tick exceeded budget");
            totalEvaluated += s.evaluated();
            maxOverflow = Math.max(maxOverflow, s.overflowSize());
        }
        assertTrue(totalEvaluated > 0);
        assertTrue(maxOverflow > 0, "with 200 NPCs and budget 4 we should accumulate overflow");
    }

    @Test
    void smearsLoadAcrossTicks() {
        World w = new World();
        for (int i = 0; i < 200; i++) {
            w.put(withTier(Fixtures.npc(UUID.randomUUID(), "v", i, 0), Tier.ACTIVE));
        }
        var sch = scheduler(1000);

        int[] perTick = new int[20];
        for (long t = 0; t < 20; t++) {
            SchedulerStats s = sch.tick(t, t, w.all(), w.lookup(),
                    new ActionStateApplier(), r -> { w.put(r); return r; });
            perTick[(int) t] = s.evaluated();
        }
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int n : perTick) { if (n < min) min = n; if (n > max) max = n; }
        assertTrue(min > 0, "some ticks did no work — bucket distribution failed");
        assertTrue(max - min < 200, "load was not smeared at all");
    }

    @Test
    void noWriteWhenActionUnchanged() {
        World w = new World();
        var hungry = withTier(Fixtures.npc(UUID.randomUUID(), "v", 0, 0), Tier.ACTIVE);
        var v = hungry.vitals();
        hungry = new NPCRecord(
                hungry.dataVersion(), hungry.identity(), hungry.location(),
                new com.lightmare.villagerrealms.core.record.Vitals(v.health(), 1f, 1f, v.mood()),
                hungry.inventory(), hungry.economy(), hungry.role(),
                hungry.factionId(), hungry.relationships(), hungry.memory(), hungry.action());
        w.put(hungry);
        UUID uid = hungry.identity().uuid();

        var sch = scheduler(8);
        long bucket = Math.floorMod(uid.getMostSignificantBits() ^ uid.getLeastSignificantBits(), 20L);

        sch.tick(bucket, bucket, w.all(), w.lookup(),
                new ActionStateApplier(), r -> { w.put(r); return r; });
        var firstAction = w.npcs.get(uid).action();
        assertEquals("eat", firstAction.actionId());

        sch.tick(bucket + 20L, bucket + 20L, w.all(), w.lookup(),
                new ActionStateApplier(), r -> { w.put(r); return r; });
        var secondAction = w.npcs.get(uid).action();

        // Same action picked → ActionState not replaced → startedAtTick unchanged.
        assertEquals("eat", secondAction.actionId());
        assertEquals(firstAction.startedAtTick(), secondAction.startedAtTick());
    }
}
