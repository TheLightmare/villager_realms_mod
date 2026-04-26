package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.considerations.WorkTimeConsideration;

import java.util.List;

/**
 * "Go to my workplace and work." Plans pathfind -&gt; work. The work
 * substep's effects (produce/consume bundle from the role) only fire when
 * the entity is physically at the workstation — see ActiveStepRuntime —
 * and repeat at WORK_CADENCE_TICKS until evaluation picks a different
 * action (e.g. WorkTimeConsideration drops at dusk, hunger overrides).
 */
public final class WorkAction implements Action {

    public static final String ID = "work";

    private final List<Consideration> considerations = List.of(new WorkTimeConsideration());

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return considerations; }
    @Override public boolean essential() { return false; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        var role = ctx.npc().role();
        String workplace = role == null ? "" : role.workplaceRef();
        return List.of(
                new ActionStep("pathfind", workplace == null ? "" : workplace),
                new ActionStep("work", role == null ? "" : role.roleId()));
    }
}
