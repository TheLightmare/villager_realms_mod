package com.lightmare.villagerrealms.core.ai;

import java.util.List;

/**
 * Result of evaluating a candidate set against an EvalContext. Carries the
 * winning action, its utility, and the planned step sequence so callers do
 * not have to call {@link Action#plan} again.
 */
public record Decision(Action action, float utility, List<ActionStep> steps) {
    public Decision {
        if (action == null) throw new IllegalArgumentException("action required");
        if (steps == null) steps = List.of();
    }
}
