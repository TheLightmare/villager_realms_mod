package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.economy.Foods;
import com.lightmare.villagerrealms.core.economy.MarketPricing;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;

/**
 * Wraps a {@link VillageRecord} as a {@link VillageView}. The view never
 * mutates; if the underlying record changes, callers must build a new view
 * (cheap — record + thin wrapper).
 *
 * communalFoodUnits and marketFoodUnits both report stockpile food today
 * — there is no "house chest" model in v1, so the village's only food
 * pool is the market.
 */
public final class RecordVillageView implements VillageView {

    private final VillageRecord village;

    public RecordVillageView(VillageRecord village) {
        if (village == null) throw new IllegalArgumentException("village required");
        this.village = village;
    }

    @Override public String villageId() { return village.villageId(); }

    @Override
    public int communalFoodUnits() {
        return marketFoodUnits();
    }

    @Override
    public int freeBeds() {
        int free = 0;
        for (VillageBed b : village.beds()) if (b.occupant() == null) free++;
        return free;
    }

    @Override
    public int marketFoodUnits() {
        VillageMarket m = village.market();
        int total = 0;
        for (var e : m.stockpile().entrySet()) {
            if (Foods.isFood(e.getKey())) total += e.getValue();
        }
        return total;
    }

    @Override
    public String cheapestFoodInMarket() {
        VillageMarket m = village.market();
        String best = null;
        long bestPrice = Long.MAX_VALUE;
        for (var e : m.stockpile().entrySet()) {
            String id = e.getKey();
            if (e.getValue() <= 0) continue;
            if (!Foods.isFood(id)) continue;
            long p = MarketPricing.currentPrice(m, id);
            if (p < bestPrice) {
                bestPrice = p;
                best = id;
            }
        }
        return best;
    }

    @Override
    public long marketPriceOf(String itemId) {
        return MarketPricing.currentPrice(village.market(), itemId);
    }

    @Override
    public long villageGold() {
        return village.market().goldLedger();
    }
}
