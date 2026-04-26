package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.record.VillageMarket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MarketOpsTest {

    @Test
    void addStockBumpsStockpileAndSupply() {
        VillageMarket m = VillageMarket.EMPTY;
        m = MarketOps.addStock(m, "minecraft:wheat", 3);
        assertEquals(3, m.stockOf("minecraft:wheat"));
        assertEquals(3, m.supplyOf("minecraft:wheat"));
        assertEquals(0, m.demandOf("minecraft:wheat"));
    }

    @Test
    void withdrawClampsAtAvailable() {
        VillageMarket m = MarketOps.addStock(VillageMarket.EMPTY, "minecraft:bread", 2);
        m = MarketOps.withdraw(m, "minecraft:bread", 5);
        assertEquals(0, m.stockOf("minecraft:bread"));
    }

    @Test
    void withdrawDoesNotTouchSupplyOrDemand() {
        VillageMarket m = MarketOps.addStock(VillageMarket.EMPTY, "minecraft:bread", 5);
        m = MarketOps.withdraw(m, "minecraft:bread", 2);
        assertEquals(5, m.supplyOf("minecraft:bread"));
        assertEquals(0, m.demandOf("minecraft:bread"));
    }

    @Test
    void bumpDemandIsIndependentOfStock() {
        VillageMarket m = MarketOps.bumpDemand(VillageMarket.EMPTY, "minecraft:bread", 4);
        assertEquals(4, m.demandOf("minecraft:bread"));
        assertEquals(0, m.stockOf("minecraft:bread"));
    }

    @Test
    void adjustGoldAccumulates() {
        VillageMarket m = MarketOps.adjustGold(VillageMarket.EMPTY, 10);
        m = MarketOps.adjustGold(m, -3);
        assertEquals(7, m.goldLedger());
    }

    @Test
    void zeroDeltaIsNoOp() {
        VillageMarket m = VillageMarket.EMPTY;
        assertSame(m, MarketOps.adjustGold(m, 0));
        assertSame(m, MarketOps.addStock(m, "x", 0));
        assertSame(m, MarketOps.withdraw(m, "x", 0));
    }

    @Test
    void decayShrinksCounters() {
        VillageMarket m = VillageMarket.EMPTY;
        m = MarketOps.addStock(m, "minecraft:wheat", 10);
        m = MarketOps.bumpDemand(m, "minecraft:bread", 6);
        m = MarketOps.decay(m, 0.5f);
        assertEquals(5, m.supplyOf("minecraft:wheat"));
        assertEquals(3, m.demandOf("minecraft:bread"));
        // Decay does not touch stockpile.
        assertEquals(10, m.stockOf("minecraft:wheat"));
    }
}
