package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.considerations.NightTimeConsideration;
import com.lightmare.villagerrealms.core.ai.considerations.SleepConsideration;

import java.util.List;

/**
 * "Go to my bed and sleep." Gated on tiredness AND vanilla bedtime: the
 * NightTimeConsideration zeroes utility during the day, so even an
 * exhausted NPC won't seek a bed until evening — matching vanilla
 * villager bed timing.
 *
 * The active runtime ({@code ActiveStepRuntime#handleSleep}) drives the
 * NPC to its assigned bed and calls {@link net.minecraft.world.entity.LivingEntity#startSleeping}
 * so the entity enters the vanilla SLEEPING pose and the bed flips
 * OCCUPIED. Energy restoration ticks via {@link com.lightmare.villagerrealms.core.economy.Substeps#sleep}.
 */
public final class SleepAction implements Action {

    public static final String ID = "sleep";

    private final List<Consideration> considerations =
            List.of(new SleepConsideration(), new NightTimeConsideration());

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return considerations; }
    @Override public boolean essential() { return true; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        return List.of(
                new ActionStep("pathfind", "bed"),
                new ActionStep("sleep", ""));
    }
}
