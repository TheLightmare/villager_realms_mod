package com.lightmare.villagerrealms.core.ai.actions;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;

import java.util.List;

/**
 * Floor action. Always scores a small constant so the evaluator never
 * returns null when no other action is viable. Marker steps only.
 */
public final class IdleAction implements Action {

    public static final String ID = "idle";

    private static final Consideration FLOOR = new Consideration() {
        @Override public String id() { return "idle_floor"; }
        @Override public boolean essential() { return true; }
        @Override public long ttlTicks() { return 0L; }
        @Override public float score(EvalContext ctx) { return 0.05f; }
    };

    @Override public String id() { return ID; }
    @Override public List<Consideration> considerations() { return List.of(FLOOR); }
    @Override public boolean essential() { return true; }

    @Override
    public List<ActionStep> plan(EvalContext ctx) {
        return List.of(new ActionStep("idle", ""));
    }
}
