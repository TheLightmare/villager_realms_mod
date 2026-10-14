package com.lightmare.villagerrealms.core.birth;

import com.lightmare.villagerrealms.core.ai.VillageView;
import com.lightmare.villagerrealms.core.consequence.MemoryKinds;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Vitals;

/**
 * Pure readiness-to-conceive rules, shared by the Tier 0/1 consideration
 * and the Tier 2 sweep so both fidelities agree on who may have a child
 * (CLAUDE.md: shared considerations, tuning one tunes both).
 */
public final class Readiness {

    private Readiness() {}

    /** Could this NPC be a parent at all right now? (No village-level checks.) */
    public static boolean isEligibleParent(NPCRecord npc, long now) {
        if (npc == null || npc.role() == null) return false;
        if (Births.CHILD_ROLE.equals(npc.role().roleId())) return false;
        int age = npc.identity().age();
        if (age < Births.ADULT_AGE || age > Births.MAX_PARENT_AGE) return false;
        Vitals v = npc.vitals();
        if (v.health() <= 0f) return false;
        return !inCooldown(npc, now);
    }

    /** Eligible, well fed, rested, and the village has a free bed and food. */
    public static boolean isReady(NPCRecord npc, VillageView view, long now) {
        if (!isEligibleParent(npc, now)) return false;
        Vitals v = npc.vitals();
        if (v.hunger() < Births.MIN_HUNGER || v.energy() < Births.MIN_ENERGY) return false;
        if (view == null) return false;
        return view.freeBeds() > 0 && view.marketFoodUnits() > 0;
    }

    public static float score(NPCRecord npc, VillageView view, long now) {
        return isReady(npc, view, now) ? 1f : 0f;
    }

    /**
     * True while the NPC's most recent conception or birth is younger than
     * {@link Births#CONCEIVE_COOLDOWN_TICKS}. Read from memory; the ring
     * buffer is large enough that the event can't roll off within the
     * cooldown unless something very eventful happens, and if it does the
     * cooldown merely ends early — plausible, not bit-identical.
     */
    public static boolean inCooldown(NPCRecord npc, long now) {
        if (npc.memory() == null) return false;
        for (MemoryEvent e : npc.memory().events()) {
            if (MemoryKinds.CHILD_CONCEIVED.equals(e.kind()) || MemoryKinds.CHILD_BORN.equals(e.kind())) {
                if (now - e.tick() < Births.CONCEIVE_COOLDOWN_TICKS) return true;
            }
        }
        return false;
    }
}
