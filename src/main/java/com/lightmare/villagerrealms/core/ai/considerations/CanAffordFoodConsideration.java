package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.VillageView;

/**
 * "I can afford the cheapest food in the market." Score is 1 when gold is
 * sufficient, 0 otherwise. Re-evaluated every tick because gold and price
 * both move on transactions.
 */
public final class CanAffordFoodConsideration implements Consideration {

    public static final String ID = "can_afford_food";

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 0L; }

    @Override
    public float score(EvalContext ctx) {
        VillageView v = ctx.villageView();
        if (v == null) return 0f;
        String food = v.cheapestFoodInMarket();
        if (food == null) return 0f;
        long price = v.marketPriceOf(food);
        long gold = ctx.npc().economy().gold();
        return gold >= price ? 1f : 0f;
    }
}
