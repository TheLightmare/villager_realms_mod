package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubstepsTest {

    private static NPCRecord withVitals(NPCRecord r, Vitals v) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(),
                v, r.inventory(), r.economy(), r.role(),
                r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static NPCRecord withInventory(NPCRecord r, NPCInventory inv) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(),
                r.vitals(), inv, r.economy(), r.role(),
                r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static NPCRecord withEconomy(NPCRecord r, EconomicState e) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(),
                r.vitals(), r.inventory(), e, r.role(),
                r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static NPCRecord withRole(NPCRecord r, String roleId) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(),
                r.vitals(), r.inventory(), r.economy(),
                new RoleState(roleId, "ws@0,64,0", 0L),
                r.factionId(), r.relationships(), r.memory(), r.action());
    }

    @Test
    void consumeRemovesFoodAndRestoresHunger() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 5f, 1f, 0.5f));
        NPCRecord after = Substeps.consume(npc, "minecraft:bread", 200L);

        assertEquals(10f, after.vitals().hunger(), 0.0001f);
        int breadAfter = 0;
        for (var e : after.inventory().items()) {
            if (e.itemId().equals("minecraft:bread")) breadAfter += e.count();
        }
        assertEquals(2, breadAfter);
    }

    @Test
    void consumeNoOpsWhenNoFood() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withInventory(npc, NPCInventory.EMPTY);
        npc = withVitals(npc, new Vitals(20f, 1f, 1f, 0.5f));
        assertSame(npc, Substeps.consume(npc, "minecraft:bread", 100L));
    }

    @Test
    void hungerCapsAtTwenty() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 19f, 1f, 0.5f));
        NPCRecord after = Substeps.consume(npc, "minecraft:bread", 100L);
        assertEquals(20f, after.vitals().hunger(), 0.0001f);
    }

    @Test
    void farmerWorkProducesAndPays() {
        var markets = new MapMarkets();
        markets.put("v1", VillageMarket.EMPTY);

        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withRole(npc, "farmer");
        npc = withEconomy(npc, new EconomicState(0L, List.of(), List.of()));

        NPCRecord after = Substeps.work(npc, markets, 500L);
        VillageMarket m = markets.get("v1").orElseThrow();
        assertEquals(1, m.stockOf("minecraft:wheat"));
        assertEquals(1, m.supplyOf("minecraft:wheat"));
        assertEquals(MarketPricing.basePrice("minecraft:wheat"), after.economy().gold());
    }

    @Test
    void bakerWorkRequiresWheatStock() {
        var markets = new MapMarkets();
        markets.put("v1", VillageMarket.EMPTY);

        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withRole(npc, "baker");
        npc = withEconomy(npc, new EconomicState(0L, List.of(), List.of()));

        NPCRecord nope = Substeps.work(npc, markets, 500L);
        assertEquals(0, markets.get("v1").orElseThrow().stockOf("minecraft:bread"));
        assertEquals(0L, nope.economy().gold());

        markets.update("v1", m -> MarketOps.addStock(m, "minecraft:wheat", 5));
        NPCRecord ok = Substeps.work(nope, markets, 520L);
        VillageMarket m = markets.get("v1").orElseThrow();
        assertEquals(4, m.stockOf("minecraft:wheat"));
        assertEquals(1, m.stockOf("minecraft:bread"));
        assertTrue(ok.economy().gold() > 0L);
    }

    @Test
    void laborerWorkIsNoOp() {
        var markets = new MapMarkets();
        markets.put("v1", VillageMarket.EMPTY);

        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withRole(npc, "laborer");
        npc = withEconomy(npc, new EconomicState(0L, List.of(), List.of()));

        NPCRecord after = Substeps.work(npc, markets, 500L);
        assertEquals(0L, after.economy().gold());
        assertEquals(0, markets.get("v1").orElseThrow().stockpile().size());
    }

    @Test
    void buyDebitsGoldCreditsInventoryAndIncrementsDemand() {
        var markets = new MapMarkets();
        markets.put("v1", MarketOps.addStock(VillageMarket.EMPTY, "minecraft:bread", 5));

        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withInventory(npc, NPCInventory.EMPTY);
        npc = withEconomy(npc, new EconomicState(20L, List.of(), List.of()));

        long price = MarketPricing.currentPrice(markets.get("v1").orElseThrow(), "minecraft:bread");
        NPCRecord after = Substeps.buy(npc, "minecraft:bread", markets, 1000L);
        VillageMarket m = markets.get("v1").orElseThrow();
        assertEquals(4, m.stockOf("minecraft:bread"));
        assertEquals(1, m.demandOf("minecraft:bread"));
        assertEquals(price, m.goldLedger());
        assertEquals(20L - price, after.economy().gold());

        var bought = after.inventory().items().stream()
                .filter(e -> e.itemId().equals("minecraft:bread")).findFirst().orElseThrow();
        assertEquals(Provenance.BOUGHT, bought.provenance());
        assertEquals(1, bought.count());
    }

    @Test
    void buyNoOpsWhenBroke() {
        var markets = new MapMarkets();
        markets.put("v1", MarketOps.addStock(VillageMarket.EMPTY, "minecraft:bread", 5));

        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withInventory(npc, NPCInventory.EMPTY);
        npc = withEconomy(npc, new EconomicState(0L, List.of(), List.of()));

        NPCRecord after = Substeps.buy(npc, "minecraft:bread", markets, 1000L);
        assertEquals(5, markets.get("v1").orElseThrow().stockOf("minecraft:bread"));
        assertEquals(0L, after.economy().gold());
        assertTrue(after.inventory().items().isEmpty());
    }

    @Test
    void drainReducesHungerAndEnergyProportionally() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 20f, 1f, 0.5f));

        NPCRecord after = Substeps.drain(npc, 1200L);
        assertEquals(20f - 1f, after.vitals().hunger(), 0.0001f,
                "1200 ticks should drain exactly 1 hunger");

        // Energy decay over 24000 ticks = 1.0; over 1200 = 0.05.
        assertEquals(1f - 0.05f, after.vitals().energy(), 0.0001f);
    }

    @Test
    void drainClampsAtZero() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 0.1f, 0.01f, 0.5f));
        NPCRecord after = Substeps.drain(npc, 100_000L);
        assertEquals(0f, after.vitals().hunger(), 0.0001f);
        assertEquals(0f, after.vitals().energy(), 0.0001f);
    }

    @Test
    void drainIsNoopAtZeroVitals() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 0f, 0f, 0.5f));
        assertSame(npc, Substeps.drain(npc, 1200L));
    }

    @Test
    void drainIsNoopForZeroElapsed() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 5f, 0.5f, 0.5f));
        assertSame(npc, Substeps.drain(npc, 0L));
    }

    @Test
    void sleepRestoresEnergyProportionally() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 20f, 0f, 0.5f));
        NPCRecord after = Substeps.sleep(npc, 200L);
        // 200 / 400 = 0.5 restored.
        assertEquals(0.5f, after.vitals().energy(), 0.0001f);
    }

    @Test
    void sleepCapsAtMaxEnergy() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 20f, 0.9f, 0.5f));
        NPCRecord after = Substeps.sleep(npc, 10_000L);
        assertEquals(1.0f, after.vitals().energy(), 0.0001f);
    }

    @Test
    void sleepIsNoopAtMaxEnergy() {
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withVitals(npc, new Vitals(20f, 20f, 1.0f, 0.5f));
        assertSame(npc, Substeps.sleep(npc, 1000L));
    }

    @Test
    void withdrawTakesItemFromMarketWithoutPayment() {
        var markets = new MapMarkets();
        markets.put("v1", MarketOps.addStock(VillageMarket.EMPTY, "minecraft:wheat", 3));

        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v1", 0, 0);
        npc = withInventory(npc, NPCInventory.EMPTY);
        npc = withEconomy(npc, new EconomicState(10L, List.of(), List.of()));

        NPCRecord after = Substeps.withdraw(npc, "minecraft:wheat", markets, 1500L);
        assertEquals(10L, after.economy().gold());
        assertEquals(2, markets.get("v1").orElseThrow().stockOf("minecraft:wheat"));
        var taken = after.inventory().items().stream()
                .filter(e -> e.itemId().equals("minecraft:wheat")).findFirst().orElseThrow();
        assertEquals(Provenance.GIFTED, taken.provenance());
        assertEquals(1, taken.count());
    }

}
