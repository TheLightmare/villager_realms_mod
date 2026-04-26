package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.ai.Decision;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.NPCRecord;

/**
 * Default DecisionApplier: if the picked action differs from what the NPC
 * is already doing, replace ActionState with a fresh entry; otherwise no
 * write happens (and the registry stays clean of pointless dirty marks).
 *
 * v1 only writes the action ID — sub-step execution is the next layer.
 */
public final class ActionStateApplier implements AIScheduler.DecisionApplier {

    @Override
    public NPCRecord apply(NPCRecord npc, Decision decision, long tick) {
        ActionState prior = npc.action();
        String chosen = decision.action().id();
        if (prior != null && chosen.equals(prior.actionId())) {
            return npc;
        }
        ActionState next = new ActionState(chosen, 0, tick, "");
        return new NPCRecord(
                npc.dataVersion(),
                npc.identity(),
                npc.location(),
                npc.vitals(),
                npc.inventory(),
                npc.economy(),
                npc.role(),
                npc.factionId(),
                npc.relationships(),
                npc.memory(),
                next);
    }
}
