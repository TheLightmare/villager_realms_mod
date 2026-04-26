package com.lightmare.villagerrealms.core.ai;

import java.util.List;

/**
 * A candidate behavior the evaluator can pick. Carries the considerations
 * whose product produces its utility, plus a base weight that lets the
 * designer bias one action over another at equal consideration scores.
 *
 * Actions are atomic at the sub-step level. Step planning happens at
 * commit time (when the evaluator picks the action), not during scoring.
 * Re-evaluation is allowed only between sub-steps.
 */
public interface Action {

    /** Stable ID; used in ActionState.actionId and as the cache discriminator. */
    String id();

    /** Considerations whose scores combine into utility. Order has no effect. */
    List<Consideration> considerations();

    /**
     * Base weight in [0, 1]. Final utility is multiplied by this, so a
     * weight of 1.0 means "let the considerations decide alone". Lower
     * values bias the action down even when its considerations score high.
     */
    default float baseWeight() { return 1.0f; }

    /**
     * True if this action belongs to the stripped (Tier 1) action set.
     * Tier 1 only evaluates essential actions to keep load proportional.
     */
    boolean essential();

    /**
     * Plan the concrete sub-step sequence at the moment the action is
     * picked. v1 returns marker steps only — actual world effects come
     * online in step 5 (economy). Returning an empty list is allowed and
     * means the action is a no-op for now.
     */
    List<ActionStep> plan(EvalContext ctx);
}
