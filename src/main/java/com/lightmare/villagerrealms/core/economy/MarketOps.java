package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.record.VillageMarket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-function helpers for {@link VillageMarket}. Each call returns a new
 * market so callers can hand the result to {@code VillageRecord.withMarket}.
 */
public final class MarketOps {

    private MarketOps() {}

    /** Producer drops {@code count} units of {@code itemId} into the market. */
    public static VillageMarket addStock(VillageMarket m, String itemId, int count) {
        if (count <= 0) return m;
        Map<String, Integer> stock = mut(m.stockpile());
        Map<String, Integer> sup = mut(m.supply());
        stock.merge(itemId, count, Integer::sum);
        sup.merge(itemId, count, Integer::sum);
        return new VillageMarket(stock, sup, m.demand(), m.goldLedger());
    }

    /**
     * Withdraw up to {@code count} of {@code itemId} from market stock. Returns
     * the new market; the actual removed quantity is the minimum of available
     * stock and requested count.
     */
    public static VillageMarket withdraw(VillageMarket m, String itemId, int count) {
        if (count <= 0) return m;
        int available = m.stockOf(itemId);
        int take = Math.min(available, count);
        if (take <= 0) return m;
        Map<String, Integer> stock = mut(m.stockpile());
        int remaining = available - take;
        if (remaining > 0) stock.put(itemId, remaining);
        else stock.remove(itemId);
        return new VillageMarket(stock, m.supply(), m.demand(), m.goldLedger());
    }

    /** Buyer triggers a demand bump (without taking stock). Use with withdraw. */
    public static VillageMarket bumpDemand(VillageMarket m, String itemId, int count) {
        if (count <= 0) return m;
        Map<String, Integer> dem = mut(m.demand());
        dem.merge(itemId, count, Integer::sum);
        return new VillageMarket(m.stockpile(), m.supply(), dem, m.goldLedger());
    }

    /** Add (positive) or subtract (negative) gold from the village's ledger. */
    public static VillageMarket adjustGold(VillageMarket m, long delta) {
        if (delta == 0L) return m;
        return new VillageMarket(m.stockpile(), m.supply(), m.demand(), m.goldLedger() + delta);
    }

    /**
     * Multiplicative decay applied to supply/demand counters. Caller passes
     * a fraction in [0, 1]; we round each entry. v1 calls this on autosave
     * cadence to keep the price signal responsive without losing it after
     * one transaction.
     */
    public static VillageMarket decay(VillageMarket m, float keepFraction) {
        if (keepFraction < 0f) keepFraction = 0f;
        if (keepFraction > 1f) keepFraction = 1f;
        Map<String, Integer> sup = decayMap(m.supply(), keepFraction);
        Map<String, Integer> dem = decayMap(m.demand(), keepFraction);
        return new VillageMarket(m.stockpile(), sup, dem, m.goldLedger());
    }

    private static Map<String, Integer> mut(Map<String, Integer> src) {
        return new LinkedHashMap<>(src);
    }

    private static Map<String, Integer> decayMap(Map<String, Integer> src, float keep) {
        Map<String, Integer> out = new LinkedHashMap<>(src.size());
        for (var e : src.entrySet()) {
            int v = Math.round(e.getValue() * keep);
            if (v > 0) out.put(e.getKey(), v);
        }
        return out;
    }
}
