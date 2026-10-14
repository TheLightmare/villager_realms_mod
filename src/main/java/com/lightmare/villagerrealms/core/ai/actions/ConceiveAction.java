package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.considerations.ReadyToConceiveConsideration;

import java.util.List;

/**
 * "Go home and start a family." Plans pathfind -&gt; conceive. The conceive
 * substep (active runtime) picks a partner and reserves a bed; the memory
 * event it writes drops {@link ReadyToConceiveConsideration} to zero for
 * the cooldown, so the action can't repeat.
 *
 * Low base weight: this only wins when nothing pressing competes — a
 * rested, fed NPC at night with no work. Not essential (Tier 0 only).
 */
public final class ConceiveAction implements Action {

    public static final String ID = "conceive";

    private final List<Consideration> considerations = List.of(new ReadyToConceiveConsideration());

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return considerations; }
    @Override public boolean essential() { return false; }
    @Override public float baseWeight() { return 0.3f; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        return List.of(
                new ActionStep("pathfind", "bed"),
                new ActionStep("conceive", ""));
    }
}
