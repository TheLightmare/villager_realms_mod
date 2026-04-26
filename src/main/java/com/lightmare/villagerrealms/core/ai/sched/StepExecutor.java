package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.ai.Decision;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.NPCRecord;

/**
 * Action-selection applier. The AI scheduler is purely a *picker*: when the
 * picked action differs from the prior one it writes a fresh ActionState
 * (subStep = 0, startedAtTick = now). When the same action is picked again
 * it does nothing — substep advancement and effect execution are the
 * responsibility of the per-tier execution layer:
 *
 *   - Tier 0/1 (loaded chunks): {@code ActiveStepRuntime} runs effects only
 *     when entity geometry says the NPC has actually arrived at its target.
 *   - Tier 2 (unloaded chunks): the abstract simulator (future) applies
 *     coarser aggregate rules per CLAUDE.md.
 *
 * Mutating economy/inventory/vitals from the scheduler would couple Tier 0
 * effects to the AI cadence rather than to the entity's physical state,
 * which CLAUDE.md explicitly forbids.
 */
public final class StepExecutor implements AIScheduler.DecisionApplier {

    @Override
    public NPCRecord apply(NPCRecord npc, Decision decision, long tick) {
        ActionState prior = npc.action();
        String chosen = decision.action().id();
        if (prior != null && chosen.equals(prior.actionId())) {
            return npc;
        }
        ActionState fresh = new ActionState(chosen, 0, tick, "");
        return new NPCRecord(
                npc.dataVersion(), npc.identity(), npc.location(),
                npc.vitals(), npc.inventory(), npc.economy(), npc.role(),
                npc.factionId(), npc.relationships(), npc.memory(), fresh);
    }
}
