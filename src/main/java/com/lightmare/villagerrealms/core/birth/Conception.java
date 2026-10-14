package com.lightmare.villagerrealms.core.birth;

import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.MemoryKinds;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.ScheduledEvent;
import com.lightmare.villagerrealms.core.record.VillageRecord;

import java.util.Map;
import java.util.UUID;

/**
 * Pure conception: reserves a bed for the unborn child, schedules the
 * birth, and stamps both parents (memory event → cooldown, family bond).
 * The caller commits the three results to their stores.
 */
public final class Conception {

    public record Plan(
            UUID childUuid,
            int bedIndex,
            VillageRecord village,
            NPCRecord mother,
            NPCRecord father,
            ScheduledEvent birth
    ) {}

    private Conception() {}

    /** Null if the village has no free bed. */
    public static Plan plan(NPCRecord mother, NPCRecord father, VillageRecord village, long now, UUID childUuid) {
        if (mother == null || father == null || village == null) return null;
        if (childUuid == null) throw new IllegalArgumentException("childUuid required");
        int idx = village.firstFreeBedIndex();
        if (idx < 0) return null;

        VillageRecord reserved = village.reserveBed(idx, childUuid);
        ScheduledEvent birth = new ScheduledEvent(
                UUID.randomUUID(), now + Births.PREGNANCY_TICKS, ScheduleKinds.BIRTH,
                village.villageId(), childUuid,
                Map.of(ScheduleKinds.DATA_MOTHER, mother.identity().uuid().toString(),
                        ScheduleKinds.DATA_FATHER, father.identity().uuid().toString()));

        NPCRecord m = stamp(mother, father, childUuid, now);
        NPCRecord f = stamp(father, mother, childUuid, now);
        return new Plan(childUuid, idx, reserved, m, f, birth);
    }

    private static NPCRecord stamp(NPCRecord parent, NPCRecord partner, UUID child, long now) {
        NPCRecord out = Consequences.remember(parent, new MemoryEvent(
                now, MemoryKinds.CHILD_CONCEIVED, Refs.npc(partner.identity().uuid()), child.toString()));
        return Consequences.adjustPersonal(out, partner.identity().uuid(), Births.FAMILY_BOND);
    }
}
