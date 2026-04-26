package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.economy.MapMarkets;
import com.lightmare.villagerrealms.core.economy.Substeps;
import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractTickTest {

    private static NPCRecord asDormant(NPCRecord r) {
        Location l = r.location();
        Location nl = new Location(l.homeVillageId(), l.x(), l.y(), l.z(), l.dimension(), Tier.DORMANT);
        return new NPCRecord(
                r.dataVersion(), r.identity(), nl, r.vitals(), r.inventory(),
                r.economy(), r.role(), r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static NPCRecord withHunger(NPCRecord r, float hunger) {
        Vitals v = r.vitals();
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(),
                new Vitals(v.health(), hunger, v.energy(), v.mood()),
                r.inventory(), r.economy(), r.role(), r.factionId(),
                r.relationships(), r.memory(), r.action());
    }

    private static NPCRecord withInventory(NPCRecord r, NPCInventory inv) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(), r.vitals(), inv,
                r.economy(), r.role(), r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static long workTimeAt() {
        // Pick a tick clearly in the work-time half of the day. The schedule
        // offset on Fixtures NPCs is 1234, so "noon" = 6000 - 1234.
        return 6000L - 1234L;
    }

    private static long sleepTimeAt() {
        // Late evening, past the 12000-tick work cutoff.
        return 18000L - 1234L;
    }

    @Test
    void hungerDrainsLinearlyOverManyCycles() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = withInventory(rec, NPCInventory.EMPTY);
        rec = withHunger(rec, 20f);

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        long from = sleepTimeAt();
        long to = from + AbstractTick.CADENCE_TICKS * 12L;
        NPCRecord after = AbstractTick.advance(rec, from, to, markets);

        float drainPerCycle = Substeps.HUNGER_DRAIN_PER_TICK * AbstractTick.CADENCE_TICKS;
        float expected = 20f - 12f * drainPerCycle;
        assertEquals(expected, after.vitals().hunger(), 0.01f);
    }

    @Test
    void autoEatsFromInventoryWhenHungry() {
        UUID id = UUID.randomUUID();
        NPCRecord rec = asDormant(Fixtures.npc(id, "v", 0, 0));
        rec = withInventory(rec, new NPCInventory(List.of(
                new ItemEntry("minecraft:bread", 5, Provenance.CRAFTED, 0L))));
        rec = withHunger(rec, 12f);

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        // One sleep-time cycle so work doesn't interfere.
        NPCRecord after = AbstractTick.applyCycle(rec, sleepTimeAt() + AbstractTick.CADENCE_TICKS, markets);

        // Bread restores 5; drained ~0.083 first; net ≈ +4.917.
        assertTrue(after.vitals().hunger() > rec.vitals().hunger() + 4.5f,
                "hunger should rise after auto-eating bread, got " + after.vitals().hunger());
        int bread = 0;
        for (var e : after.inventory().items()) if ("minecraft:bread".equals(e.itemId())) bread += e.count();
        assertEquals(4, bread, "one bread should have been consumed");
    }

    @Test
    void autoBuysAndEatsFromMarketWhenInventoryEmpty() {
        UUID id = UUID.randomUUID();
        NPCRecord rec = asDormant(Fixtures.npc(id, "v", 0, 0));
        rec = withInventory(rec, NPCInventory.EMPTY);
        rec = withHunger(rec, 6f);

        MapMarkets markets = new MapMarkets();
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("minecraft:bread", 10);
        markets.put("v", new VillageMarket(stock, Map.of(), Map.of(), 0L));

        long preGold = rec.economy().gold();

        NPCRecord after = AbstractTick.applyCycle(rec, sleepTimeAt() + AbstractTick.CADENCE_TICKS, markets);

        assertTrue(after.economy().gold() < preGold,
                "should have spent gold buying bread");
        assertTrue(after.vitals().hunger() > 6f,
                "should have eaten the purchased bread");
        // Market stock fell by 1 (bought) — verifies Substeps.buy ran.
        assertEquals(9, markets.get("v").orElseThrow().stockOf("minecraft:bread"));
    }

    @Test
    void doesNotBuyIfNotHungryEnough() {
        // Above AUTO_BUY_THRESHOLD but below AUTO_EAT_THRESHOLD with no inventory food.
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = withInventory(rec, NPCInventory.EMPTY);
        rec = withHunger(rec, 13f);

        MapMarkets markets = new MapMarkets();
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("minecraft:bread", 5);
        markets.put("v", new VillageMarket(stock, Map.of(), Map.of(), 0L));
        long preGold = rec.economy().gold();

        NPCRecord after = AbstractTick.applyCycle(rec, sleepTimeAt() + AbstractTick.CADENCE_TICKS, markets);

        assertEquals(preGold, after.economy().gold(),
                "shouldn't shop while only mildly hungry and inventory empty");
        assertEquals(5, markets.get("v").orElseThrow().stockOf("minecraft:bread"));
    }

    @Test
    void workerProducesDuringWorkTime() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = new NPCRecord(
                rec.dataVersion(), rec.identity(), rec.location(),
                rec.vitals(), rec.inventory(), rec.economy(),
                new RoleState("farmer", "workstation:farmer@0,64,0", 1234L),
                rec.factionId(), rec.relationships(), rec.memory(), rec.action());

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        NPCRecord after = AbstractTick.applyCycle(rec, workTimeAt(), markets);

        VillageMarket m = markets.get("v").orElseThrow();
        assertEquals(1, m.stockOf("minecraft:wheat"),
                "farmer should have deposited one wheat into the market");
    }

    @Test
    void workerDoesNotProduceOutsideWorkTime() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = new NPCRecord(
                rec.dataVersion(), rec.identity(), rec.location(),
                rec.vitals(), rec.inventory(), rec.economy(),
                new RoleState("farmer", "workstation:farmer@0,64,0", 1234L),
                rec.factionId(), rec.relationships(), rec.memory(), rec.action());

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        NPCRecord after = AbstractTick.applyCycle(rec, sleepTimeAt(), markets);

        VillageMarket m = markets.get("v").orElseThrow();
        assertEquals(0, m.stockOf("minecraft:wheat"),
                "no production should occur outside work-time");
    }

    @Test
    void laborerDoesNotProduce() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = new NPCRecord(
                rec.dataVersion(), rec.identity(), rec.location(),
                rec.vitals(), rec.inventory(), rec.economy(),
                new RoleState("laborer", null, 0L),
                rec.factionId(), rec.relationships(), rec.memory(), rec.action());

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        NPCRecord after = AbstractTick.applyCycle(rec, workTimeAt(), markets);

        // Laborer is not a worker; market should stay empty.
        VillageMarket m = markets.get("v").orElseThrow();
        assertTrue(m.stockpile().isEmpty(), "non-worker shouldn't produce anything");
    }

    @Test
    void advanceClampsAtMaxCycles() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = withInventory(rec, NPCInventory.EMPTY);
        rec = withHunger(rec, 20f);

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        long from = sleepTimeAt();
        // Way more than MAX_CYCLES_PER_CALL worth of elapsed.
        long to = from + AbstractTick.CADENCE_TICKS * (AbstractTick.MAX_CYCLES_PER_CALL + 500L);
        NPCRecord after = AbstractTick.advance(rec, from, to, markets);

        float drainPerCycle = Substeps.HUNGER_DRAIN_PER_TICK * AbstractTick.CADENCE_TICKS;
        float minPossibleHunger = 20f - AbstractTick.MAX_CYCLES_PER_CALL * drainPerCycle;
        assertTrue(after.vitals().hunger() >= minPossibleHunger - 0.01f,
                "should have applied at most MAX_CYCLES_PER_CALL cycles");
    }

    @Test
    void advanceIsNoopWhenWindowIsZero() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        assertSame(rec, AbstractTick.advance(rec, 1000L, 1000L, markets));
        assertSame(rec, AbstractTick.advance(rec, 1000L, 1050L, markets),
                "less than one cadence should be a no-op");
    }

    @Test
    void schedulerSkipsNonDormantNpcs() {
        Map<UUID, NPCRecord> world = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            NPCRecord r = Fixtures.npc(UUID.randomUUID(), "v", i, 0);
            // Leave them at default Tier.NEARBY from Fixtures — not DORMANT.
            world.put(r.identity().uuid(), r);
        }
        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        AbstractScheduler sched = new AbstractScheduler(64, markets);
        SchedulerStats[] last = new SchedulerStats[1];
        for (long t = 0; t < 200; t++) {
            last[0] = sched.tick(t,
                    new java.util.ArrayList<>(world.values()),
                    id -> world.get(id),
                    r -> { world.put(r.identity().uuid(), r); return r; });
            assertEquals(0, last[0].evaluated(),
                    "non-DORMANT NPCs must never be evaluated");
        }
    }

    @Test
    void schedulerStaggersDormantNpcsAcrossBuckets() {
        Map<UUID, NPCRecord> world = new HashMap<>();
        for (int i = 0; i < 200; i++) {
            world.put(UUID.randomUUID(), null);
        }
        // Re-key with proper records now that we have stable UUIDs.
        Map<UUID, NPCRecord> finalWorld = new HashMap<>();
        for (UUID id : world.keySet()) {
            finalWorld.put(id, asDormant(Fixtures.npc(id, "v", 0, 0)));
        }
        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        AbstractScheduler sched = new AbstractScheduler(1000, markets);

        int[] perTick = new int[(int) AbstractTick.CADENCE_TICKS];
        for (long t = 0; t < AbstractTick.CADENCE_TICKS; t++) {
            SchedulerStats s = sched.tick(t,
                    new java.util.ArrayList<>(finalWorld.values()),
                    id -> finalWorld.get(id),
                    r -> { finalWorld.put(r.identity().uuid(), r); return r; });
            perTick[(int) t] = s.evaluated();
        }
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int n : perTick) { sum += n; if (n < min) min = n; if (n > max) max = n; }
        assertEquals(200, sum, "every NPC should be evaluated exactly once across 100 ticks");
        assertTrue(max - min < 200, "load should be smeared across buckets, not piled on one tick");
    }

    @Test
    void energyDrainsDuringWorkTimeCycles() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = new NPCRecord(
                rec.dataVersion(), rec.identity(), rec.location(),
                new Vitals(rec.vitals().health(), 20f, 1.0f, rec.vitals().mood()),
                rec.inventory(), rec.economy(),
                new RoleState("farmer", "workstation:farmer@0,64,0", 1234L),
                rec.factionId(), rec.relationships(), rec.memory(), rec.action());

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        long from = workTimeAt();
        long to = from + AbstractTick.CADENCE_TICKS * 50L;
        NPCRecord after = AbstractTick.advance(rec, from, to, markets);

        assertTrue(after.vitals().energy() < 1.0f,
                "energy should drain during work-time cycles, got " + after.vitals().energy());
    }

    @Test
    void energyRestoresDuringSleepTimeCycles() {
        NPCRecord rec = asDormant(Fixtures.npc(UUID.randomUUID(), "v", 0, 0));
        rec = withInventory(rec, NPCInventory.EMPTY);
        rec = new NPCRecord(
                rec.dataVersion(), rec.identity(), rec.location(),
                new Vitals(rec.vitals().health(), 20f, 0.2f, rec.vitals().mood()),
                rec.inventory(), rec.economy(), rec.role(),
                rec.factionId(), rec.relationships(), rec.memory(), rec.action());

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        long from = sleepTimeAt();
        long to = from + AbstractTick.CADENCE_TICKS * 5L;
        NPCRecord after = AbstractTick.advance(rec, from, to, markets);

        assertTrue(after.vitals().energy() > 0.2f,
                "energy should rise across sleep-time cycles, got " + after.vitals().energy());
    }

    @Test
    void catchUpIsNoopOnFirstSightAndAdvancesAfter() {
        UUID id = UUID.randomUUID();
        final NPCRecord rec = withHunger(
                withInventory(asDormant(Fixtures.npc(id, "v", 0, 0)), NPCInventory.EMPTY),
                20f);

        MapMarkets markets = new MapMarkets();
        markets.put("v", VillageMarket.EMPTY);

        AbstractScheduler sched = new AbstractScheduler(64, markets);

        // First-sight: scheduler has no lastAbstractTick → records "now" and
        // applies no cycles. This is by design; we can't fabricate dormant
        // history we never observed.
        NPCRecord first = sched.catchUp(rec, 0L);
        assertSame(rec, first, "first catch-up should be a no-op");

        // Now jump 50 cycles forward and catch up — elapsed = 5000 ticks.
        long later = AbstractTick.CADENCE_TICKS * 50L;
        NPCRecord caught = sched.catchUp(rec, later);
        assertNotSame(rec, caught, "second catch-up should advance the record");
        assertTrue(caught.vitals().hunger() < 20f, "hunger should have dropped during catch-up");
    }
}
