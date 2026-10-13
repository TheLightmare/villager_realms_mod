package com.lightmare.villagerrealms.core.ai;

/**
 * One sub-step in an action sequence. The kind is opaque to the AI layer;
 * {@code ActiveStepRuntime} (Tier 0/1) dispatches on it — {@code pathfind},
 * {@code consume}, {@code work}, {@code buy}, {@code withdraw}, {@code sleep},
 * {@code idle} — and applies effects through {@code Substeps} only when the
 * entity is physically in place. {@code target} is kind-specific: a
 * {@code "x,y,z"} coordinate, {@code "bed"}, {@code "market"}, or an item id.
 */
public record ActionStep(String kind, String target) {
    public ActionStep {
        if (kind == null) throw new IllegalArgumentException("kind required");
        if (target == null) target = "";
    }
}
