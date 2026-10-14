package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.consequence.Attitude;
import com.lightmare.villagerrealms.core.record.VillageMarket;

/**
 * Pure player ↔ village-market trade terms. The player trades with the
 * market through an NPC; the NPC's attitude sets the margin. Hostile NPCs
 * don't trade at all ({@link #canTrade}). Prices are in gold; the server
 * layer decides what a gold is to a player (emeralds).
 *
 * Buying bumps demand and pays the ledger, exactly like an NPC purchase
 * ({@code Substeps.buy}); selling adds stock and supply and draws on the
 * ledger, exactly like production ({@code Substeps.work}). The player is a
 * market participant, not a special case.
 */
public final class Trade {

    private Trade() {}

    public static boolean canTrade(Attitude a) {
        return a != null && !a.hostile();
    }

    /** Markup over market price the NPC charges a buyer. */
    public static double buyMarkup(Attitude a) {
        return switch (a) {
            case FRIENDLY -> 1.0;
            case NEUTRAL -> 1.25;
            case WARY -> 1.5;
            case HOSTILE -> Double.POSITIVE_INFINITY;
        };
    }

    /** Fraction of market price the NPC pays a seller. */
    public static double sellFactor(Attitude a) {
        return switch (a) {
            case FRIENDLY -> 0.8;
            case NEUTRAL -> 0.6;
            case WARY -> 0.4;
            case HOSTILE -> 0.0;
        };
    }

    /** Per-unit price the player pays; at least 1. */
    public static long buyPrice(VillageMarket m, String itemId, Attitude a) {
        if (!canTrade(a)) return Long.MAX_VALUE;
        long base = MarketPricing.currentPrice(m, itemId);
        return Math.max(1L, (long) Math.ceil(base * buyMarkup(a)));
    }

    /** Per-unit price the player receives; 0 means the NPC isn't interested. */
    public static long sellPrice(VillageMarket m, String itemId, Attitude a) {
        if (!canTrade(a)) return 0L;
        long base = MarketPricing.currentPrice(m, itemId);
        return (long) Math.floor(base * sellFactor(a));
    }

    /** Market after the player buys {@code count} of {@code itemId} for {@code paid} gold total. */
    public static VillageMarket buy(VillageMarket m, String itemId, int count, long paid) {
        if (count <= 0) return m;
        if (m.stockOf(itemId) < count) throw new IllegalStateException("insufficient stock of " + itemId);
        VillageMarket next = MarketOps.withdraw(m, itemId, count);
        next = MarketOps.bumpDemand(next, itemId, count);
        return MarketOps.adjustGold(next, paid);
    }

    /** Market after the player sells {@code count} of {@code itemId} for {@code paid} gold total. */
    public static VillageMarket sell(VillageMarket m, String itemId, int count, long paid) {
        if (count <= 0) return m;
        VillageMarket next = MarketOps.addStock(m, itemId, count);
        return MarketOps.adjustGold(next, -paid);
    }
}
