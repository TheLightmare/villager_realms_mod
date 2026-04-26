package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.considerations.HungerConsideration;
import com.lightmare.villagerrealms.core.economy.Foods;
import com.lightmare.villagerrealms.core.economy.InventoryOps;

import java.util.List;

/**
 * "Eat something from inventory." Picks the first food item, then plans a
 * single 'consume' substep targeting that item id. The actual hunger and
 * inventory mutation is the active runtime's job — it fires Substeps.consume
 * once the entity is standing still and the cadence has elapsed.
 */
public final class EatAction implements Action {

    public static final String ID = "eat";

    private static final Consideration HAS_FOOD = new Consideration() {
        @Override public String id() { return "has_food_in_inventory"; }
        @Override public boolean essential() { return true; }
        @Override public long ttlTicks() { return 0L; }
        @Override public float score(EvalContext ctx) {
            return InventoryOps.firstMatching(ctx.npc().inventory(), Foods::isFood) != null ? 1f : 0f;
        }
    };

    private final List<Consideration> considerations =
            List.of(new HungerConsideration(), HAS_FOOD);

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return considerations; }
    @Override public boolean essential() { return true; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        String foodId = InventoryOps.firstMatching(ctx.npc().inventory(), Foods::isFood);
        if (foodId == null) return List.of();
        return List.of(new ActionStep("consume", foodId));
    }
}
