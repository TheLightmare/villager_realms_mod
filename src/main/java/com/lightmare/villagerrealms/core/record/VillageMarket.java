package com.lightmare.villagerrealms.core.record;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-village market state. Tracks the village's communal item stockpile,
 * recent supply/demand counters per item id, and a gold ledger that pays
 * producers and receives buyer gold.
 *
 * v1 keeps everything flat — flat base prices with a soft supply/demand
 * adjustment. Inter-village exchange and currency inflation are explicitly
 * out of scope; the ledger is allowed to print money to pay producers.
 *
 * The supply/demand maps are decayed externally (caller's responsibility)
 * so this record stays a dumb data shell.
 */
public record VillageMarket(
        Map<String, Integer> stockpile,
        Map<String, Integer> supply,
        Map<String, Integer> demand,
        long goldLedger
) {
    public static final VillageMarket EMPTY =
            new VillageMarket(Map.of(), Map.of(), Map.of(), 0L);

    public VillageMarket {
        if (stockpile == null) throw new IllegalArgumentException("stockpile required");
        if (supply == null) throw new IllegalArgumentException("supply required");
        if (demand == null) throw new IllegalArgumentException("demand required");
        stockpile = Collections.unmodifiableMap(new LinkedHashMap<>(stockpile));
        supply = Collections.unmodifiableMap(new LinkedHashMap<>(supply));
        demand = Collections.unmodifiableMap(new LinkedHashMap<>(demand));
    }

    public int stockOf(String itemId) {
        Integer n = stockpile.get(itemId);
        return n == null ? 0 : n;
    }

    public int supplyOf(String itemId) {
        Integer n = supply.get(itemId);
        return n == null ? 0 : n;
    }

    public int demandOf(String itemId) {
        Integer n = demand.get(itemId);
        return n == null ? 0 : n;
    }
}
