package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.ai.actions.BuyFoodAction;
import com.lightmare.villagerrealms.core.ai.actions.EatAction;
import com.lightmare.villagerrealms.core.ai.actions.IdleAction;
import com.lightmare.villagerrealms.core.ai.actions.SleepAction;
import com.lightmare.villagerrealms.core.ai.actions.WorkAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Default v1 evaluator catalogue. The two flavors share their underlying
 * action instances; the stripped evaluator only filters by
 * {@link Action#essential()}, ensuring the same considerations control
 * behavior at both tiers.
 */
public final class Evaluators {

    private Evaluators() {}

    public static List<Action> defaultActions() {
        return List.of(
                new IdleAction(),
                new EatAction(),
                new BuyFoodAction(),
                new SleepAction(),
                new WorkAction());
    }

    public static Evaluator full() {
        return new Evaluator(defaultActions());
    }

    public static Evaluator stripped() {
        return stripped(defaultActions());
    }

    public static Evaluator stripped(List<Action> all) {
        var essential = new ArrayList<Action>(all.size());
        for (Action a : all) if (a.essential()) essential.add(a);
        return new Evaluator(essential);
    }
}
