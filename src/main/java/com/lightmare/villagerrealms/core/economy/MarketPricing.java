package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.record.VillageMarket;

import java.util.HashMap;
import java.util.Map;

/**
 * Item base prices and a soft supply/demand price formula.
 *
 * Per CLAUDE.md, v1 keeps prices "flat at first" — base prices come from
 * this static table. The dynamic adjustment is in place from day one so
 * tuning is just numbers, not architecture, but the adjustment scale is
 * conservative.
 *
 * Formula: price = base * clamp(1 + (demand - supply) / SCALE, 0.5, 2.0).
 * Items not in the table return base 1.
 */
public final class MarketPricing {

    private static final Map<String, Long> BASE = new HashMap<>();
    private static final float SCALE = 50f;
    private static final float FLOOR = 0.5f;
    private static final float CEIL  = 2.0f;

    static {
        BASE.put("minecraft:wheat",            1L);
        BASE.put("minecraft:bread",            3L);
        BASE.put("minecraft:carrot",           2L);
        BASE.put("minecraft:potato",           2L);
        BASE.put("minecraft:apple",            2L);
        BASE.put("minecraft:cooked_beef",      5L);
        BASE.put("minecraft:cooked_chicken",   4L);
        BASE.put("minecraft:cooked_porkchop",  5L);
        BASE.put("minecraft:cooked_cod",       4L);
        BASE.put("minecraft:cooked_salmon",    5L);
    }

    private MarketPricing() {}

    public static long basePrice(String itemId) {
        Long v = BASE.get(itemId);
        return v == null ? 1L : v;
    }

    public static long currentPrice(VillageMarket market, String itemId) {
        long base = basePrice(itemId);
        if (market == null) return base;
        int s = market.supplyOf(itemId);
        int d = market.demandOf(itemId);
        float adj = 1f + (d - s) / SCALE;
        if (adj < FLOOR) adj = FLOOR;
        if (adj > CEIL)  adj = CEIL;
        long p = Math.round(base * adj);
        return p < 1L ? 1L : p;
    }

    public static void register(String itemId, long base) {
        if (itemId == null) throw new IllegalArgumentException("itemId required");
        if (base <= 0L) throw new IllegalArgumentException("base must be > 0");
        BASE.put(itemId, base);
    }
}
