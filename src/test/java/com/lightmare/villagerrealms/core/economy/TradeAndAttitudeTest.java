package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.consequence.Attitude;
import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.consequence.Standing;
import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeAndAttitudeTest {

    @Test
    void attitudeBands() {
        assertEquals(Attitude.HOSTILE, Attitude.of(-30));
        assertEquals(Attitude.HOSTILE, Attitude.of(-100));
        assertEquals(Attitude.WARY, Attitude.of(-29));
        assertEquals(Attitude.WARY, Attitude.of(-1));
        assertEquals(Attitude.NEUTRAL, Attitude.of(0));
        assertEquals(Attitude.NEUTRAL, Attitude.of(19));
        assertEquals(Attitude.FRIENDLY, Attitude.of(20));
        assertTrue(Attitude.HOSTILE.hostile());
        assertFalse(Attitude.WARY.hostile());
    }

    @Test
    void attitudeTowardsPlayerFollowsStandingThenPersonalGrudge() {
        UUID player = UUID.randomUUID();
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v", 0, 0); // faction:v
        FactionRelations rel = Standing.adjust(FactionRelations.EMPTY, "faction:v", Refs.player(player), -35);
        assertEquals(Attitude.HOSTILE, Attitude.towardsPlayer(npc, player, rel));

        NPCRecord forgiving = Consequences.adjustPersonal(npc, player, 25);
        assertEquals(Attitude.FRIENDLY, Attitude.towardsPlayer(forgiving, player, rel), "personal override wins");
        assertEquals(Attitude.NEUTRAL, Attitude.towardsPlayer(npc, player, FactionRelations.EMPTY));
    }

    @Test
    void buyPricesScaleWithAttitudeAndNeverDropBelowOne() {
        VillageMarket m = MarketOps.addStock(VillageMarket.EMPTY, "minecraft:bread", 10); // base 3
        long base = MarketPricing.currentPrice(m, "minecraft:bread");
        assertEquals(base, Trade.buyPrice(m, "minecraft:bread", Attitude.FRIENDLY));
        assertEquals((long) Math.ceil(base * 1.25), Trade.buyPrice(m, "minecraft:bread", Attitude.NEUTRAL));
        assertEquals((long) Math.ceil(base * 1.5), Trade.buyPrice(m, "minecraft:bread", Attitude.WARY));
        assertEquals(Long.MAX_VALUE, Trade.buyPrice(m, "minecraft:bread", Attitude.HOSTILE));
        assertEquals(1L, Trade.buyPrice(VillageMarket.EMPTY, "minecraft:unknown_thing", Attitude.FRIENDLY));
        assertFalse(Trade.canTrade(Attitude.HOSTILE));
        assertFalse(Trade.canTrade(null));
    }

    @Test
    void sellPricesAreAFractionAndZeroMeansNotInterested() {
        VillageMarket m = VillageMarket.EMPTY; // cooked_beef base 5
        assertEquals(4L, Trade.sellPrice(m, "minecraft:cooked_beef", Attitude.FRIENDLY));
        assertEquals(3L, Trade.sellPrice(m, "minecraft:cooked_beef", Attitude.NEUTRAL));
        assertEquals(2L, Trade.sellPrice(m, "minecraft:cooked_beef", Attitude.WARY));
        assertEquals(0L, Trade.sellPrice(m, "minecraft:cooked_beef", Attitude.HOSTILE));
        assertEquals(0L, Trade.sellPrice(m, "minecraft:cobblestone", Attitude.NEUTRAL), "base 1 × 0.6 floors to 0");
    }

    @Test
    void buyingMovesStockDemandAndLedgerLikeAnNpcPurchase() {
        VillageMarket m = MarketOps.addStock(VillageMarket.EMPTY, "minecraft:bread", 5);
        VillageMarket after = Trade.buy(m, "minecraft:bread", 2, 8L);
        assertEquals(3, after.stockOf("minecraft:bread"));
        assertEquals(2, after.demandOf("minecraft:bread"));
        assertEquals(5, after.supplyOf("minecraft:bread"), "supply untouched by a purchase");
        assertEquals(8L, after.goldLedger());
        assertThrows(IllegalStateException.class, () -> Trade.buy(after, "minecraft:bread", 4, 1L));
        assertSame(m, Trade.buy(m, "minecraft:bread", 0, 0L));
    }

    @Test
    void sellingAddsStockAndSupplyAndDrawsOnTheLedger() {
        VillageMarket after = Trade.sell(VillageMarket.EMPTY, "minecraft:cooked_beef", 1, 4L);
        assertEquals(1, after.stockOf("minecraft:cooked_beef"));
        assertEquals(1, after.supplyOf("minecraft:cooked_beef"));
        assertEquals(-4L, after.goldLedger());
    }
}
