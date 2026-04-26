package com.lightmare.villagerrealms.core.ai;

/**
 * One sub-step in an action sequence. The step kind is opaque to the AI
 * layer — actual execution lives in a future runtime that knows how to
 * pathfind, interact with workstations, etc. v1 only stores the kind so
 * that the picked action can be inspected and tested.
 */
public record ActionStep(String kind, String target) {
    public ActionStep {
        if (kind == null) throw new IllegalArgumentException("kind required");
        if (target == null) target = "";
    }
}
