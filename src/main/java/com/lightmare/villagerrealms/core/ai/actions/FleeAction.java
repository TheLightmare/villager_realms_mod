package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.ThreatView;
import com.lightmare.villagerrealms.core.ai.considerations.DangerConsideration;

import java.util.List;

/**
 * "Get away." With a visible threat, a single {@code flee} substep whose
 * target is the threat's position — the active runtime runs the opposite
 * way. With no visible threat but a fresh memory of being hit, go home and
 * stay there. Essential: Tier 1 NPCs flee on memory alone.
 *
 * Listed before the needs actions in the catalogue so a tie at full
 * utility (just hit, also starving) resolves to running.
 */
public final class FleeAction implements Action {

    public static final String ID = "flee";

    private final List<Consideration> considerations = List.of(new DangerConsideration());

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return considerations; }
    @Override public boolean essential() { return true; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        ThreatView t = ctx.threats();
        if (t.present()) {
            return List.of(new ActionStep("flee", t.x() + "," + t.y() + "," + t.z()));
        }
        return List.of(
                new ActionStep("pathfind", "bed"),
                new ActionStep("idle", ""));
    }
}
