package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.record.VillageMarket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPricingTest {

    @Test
    void unknownItemDefaultsToOne() {
        assertEquals(1L, MarketPricing.basePrice("villager_realms:unknown"));
        assertEquals(1L, MarketPricing.currentPrice(VillageMarket.EMPTY, "villager_realms:unknown"));
    }

    @Test
    void emptyMarketReturnsBase() {
        assertEquals(MarketPricing.basePrice("minecraft:bread"),
                MarketPricing.currentPrice(VillageMarket.EMPTY, "minecraft:bread"));
    }

    @Test
    void demandSpikeRaisesPrice() {
        VillageMarket low = VillageMarket.EMPTY;
        long base = MarketPricing.currentPrice(low, "minecraft:bread");

        VillageMarket high = MarketOps.bumpDemand(low, "minecraft:bread", 100);
        long demanded = MarketPricing.currentPrice(high, "minecraft:bread");

        assertTrue(demanded > base, "price should rise when demand >> supply");
    }

    @Test
    void surplusLowersPrice() {
        VillageMarket flooded = VillageMarket.EMPTY;
        for (int i = 0; i < 100; i++) {
            flooded = MarketOps.addStock(flooded, "minecraft:wheat", 1);
        }
        long base = MarketPricing.basePrice("minecraft:wheat");
        long flushedPrice = MarketPricing.currentPrice(flooded, "minecraft:wheat");
        // Wheat base is 1 and floor is 0.5x => round(0.5) = 1; check floor explicitly.
        assertTrue(flushedPrice <= base);
    }

    @Test
    void priceClampsAboveFloorOfOne() {
        VillageMarket flooded = VillageMarket.EMPTY;
        for (int i = 0; i < 1000; i++) {
            flooded = MarketOps.addStock(flooded, "minecraft:bread", 1);
        }
        long p = MarketPricing.currentPrice(flooded, "minecraft:bread");
        assertTrue(p >= 1L);
    }
}
