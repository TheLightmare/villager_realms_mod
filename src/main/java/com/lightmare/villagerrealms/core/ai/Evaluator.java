package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the highest-utility action from a candidate set. Two pre-built
 * variants are exposed by {@link Evaluators}: full (every action) and
 * stripped (essential only) for Tier 0 and Tier 1 respectively.
 *
 * Identical scoring math; the only difference is the candidate filter.
 * Tuning a consideration affects both tiers.
 */
public final class Evaluator {

    private final List<Action> candidates;

    public Evaluator(List<Action> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must be non-empty");
        }
        this.candidates = List.copyOf(candidates);
    }

    public List<Action> candidates() { return candidates; }

    public Decision evaluate(EvalContext ctx, ConsiderationCache cache) {
        Action best = null;
        float bestScore = -1f;
        for (Action a : candidates) {
            float u = Utility.scoreAction(a, ctx, cache);
            if (u > bestScore) {
                bestScore = u;
                best = a;
            }
        }
        if (best == null || bestScore <= 0f) {
            best = candidates.get(0);
            bestScore = 0f;
        }
        return new Decision(best, bestScore, new ArrayList<>(best.plan(ctx)));
    }
}
