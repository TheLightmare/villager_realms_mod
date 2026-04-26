package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.VillageView;

/**
 * "There is food I can buy in this village." Returns 1 when the
 * VillageView reports any food in market stock, 0 otherwise. Cached for
 * 600 ticks (~30s) per CLAUDE.md guidance: the answer changes slowly
 * relative to one NPC's hunger tick.
 *
 * Returns 0 when the village view is unresolved (Tier 2/Cold), which is
 * fine — those tiers shouldn't try to buy through this action anyway.
 */
public final class MarketHasFoodConsideration implements Consideration {

    public static final String ID = "market_has_food";

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 600L; }

    @Override
    public float score(EvalContext ctx) {
        VillageView v = ctx.villageView();
        if (v == null) return 0f;
        return v.marketFoodUnits() > 0 ? 1f : 0f;
    }
}
