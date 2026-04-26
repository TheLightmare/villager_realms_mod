package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.VillageView;
import com.lightmare.villagerrealms.core.ai.considerations.CanAffordFoodConsideration;
import com.lightmare.villagerrealms.core.ai.considerations.HungerConsideration;
import com.lightmare.villagerrealms.core.ai.considerations.MarketHasFoodConsideration;

import java.util.List;

/**
 * "Walk to the market and buy a unit of the cheapest food." Gated on
 * hunger + market having food + the NPC's ability to pay.
 *
 * Plans pathfind -&gt; buy. The buy substep is the one with side effects;
 * pathfind is a marker until v2 wires real movement.
 *
 * Lower base weight than EatAction so a hungry NPC with food on hand eats
 * before going shopping.
 */
public final class BuyFoodAction implements Action {

    public static final String ID = "buy_food";

    private final List<Consideration> considerations = List.of(
            new HungerConsideration(),
            new MarketHasFoodConsideration(),
            new CanAffordFoodConsideration());

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return considerations; }
    @Override public boolean essential() { return true; }
    @Override public float baseWeight() { return 0.85f; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        VillageView v = ctx.villageView();
        if (v == null) return List.of();
        String food = v.cheapestFoodInMarket();
        if (food == null) return List.of();
        return List.of(
                new ActionStep("pathfind", "market"),
                new ActionStep("buy", food));
    }
}
