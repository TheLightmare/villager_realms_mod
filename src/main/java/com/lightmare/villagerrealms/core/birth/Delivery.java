package com.lightmare.villagerrealms.core.birth;

import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.MemoryKinds;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.ScheduledEvent;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.Traits;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.Vitals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure birth: claims the reserved bed, builds the child's record, moves
 * starting gold from the parents, stamps everyone's memory and family
 * bonds, and schedules the age-up. Either parent may be null (dead since
 * conception); the child is born regardless.
 */
public final class Delivery {

    public record Result(
            NPCRecord child,
            VillageRecord village,
            NPCRecord mother,
            NPCRecord father,
            ScheduledEvent ageUp
    ) {}

    private Delivery() {}

    /** Null if no bed can be claimed. */
    public static Result deliver(UUID childUuid, VillageRecord village, NPCRecord mother, NPCRecord father,
                                 long now, Gender gender, Tier tier) {
        if (childUuid == null || village == null) return null;
        VillageRecord claimed = village.occupyReserved(childUuid);
        if (claimed == null) return null;
        VillageBed bed = claimed.beds().get(claimed.bedIndexOf(childUuid));

        long gold = 0L;
        NPCRecord m = mother, f = father;
        if (m != null) { long give = Math.min(m.economy().gold(), Births.CHILD_GOLD_FROM_EACH_PARENT); gold += give; m = withGold(m, m.economy().gold() - give); }
        if (f != null) { long give = Math.min(f.economy().gold(), Births.CHILD_GOLD_FROM_EACH_PARENT); gold += give; f = withGold(f, f.economy().gold() - give); }

        Relationships bonds = Relationships.EMPTY;
        MemoryLog memory = MemoryLog.empty();
        if (m != null) {
            bonds = bonds.withOpinion(m.identity().uuid(), Births.FAMILY_BOND);
            memory = memory.append(new MemoryEvent(now, MemoryKinds.BORN_TO, Refs.npc(m.identity().uuid()), null));
        }
        if (f != null) {
            bonds = bonds.withOpinion(f.identity().uuid(), Births.FAMILY_BOND);
            memory = memory.append(new MemoryEvent(now, MemoryKinds.BORN_TO, Refs.npc(f.identity().uuid()), null));
        }

        String factionId = village.factionOwnerId() != null ? village.factionOwnerId()
                : m != null ? m.factionId() : f != null ? f.factionId() : NPCRecord.NO_FACTION;

        NPCRecord child = new NPCRecord(
                NPCRecord.CURRENT_VERSION,
                new Identity(childUuid, "Child-" + childUuid.toString().substring(0, 6), 0,
                        gender == null ? Gender.OTHER : gender, Traits.NEUTRAL),
                new Location(village.villageId(), bed.x() + 0.5, bed.y(), bed.z() + 0.5,
                        village.dimension(), tier == null ? Tier.DORMANT : tier),
                Vitals.FRESH,
                new NPCInventory(List.of(new ItemEntry(
                        Births.CHILD_STARTING_FOOD, Births.CHILD_STARTING_FOOD_COUNT, Provenance.GIFTED, now))),
                new EconomicState(gold, List.of(), List.of()),
                new RoleState(Births.CHILD_ROLE, null, 0L),
                factionId,
                bonds,
                memory,
                ActionState.IDLE);

        if (m != null) m = parentOf(m, childUuid, now);
        if (f != null) f = parentOf(f, childUuid, now);

        ScheduledEvent ageUp = new ScheduledEvent(
                UUID.randomUUID(), now + Births.CHILDHOOD_TICKS, ScheduleKinds.AGE_UP,
                village.villageId(), childUuid, Map.of());

        return new Result(child, claimed, m, f, ageUp);
    }

    private static NPCRecord parentOf(NPCRecord parent, UUID child, long now) {
        NPCRecord out = Consequences.remember(parent, new MemoryEvent(
                now, MemoryKinds.CHILD_BORN, Refs.npc(child), null));
        return Consequences.adjustPersonal(out, child, Births.FAMILY_BOND);
    }

    private static NPCRecord withGold(NPCRecord r, long gold) {
        EconomicState e = r.economy();
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(), r.vitals(), r.inventory(),
                new EconomicState(gold, e.debts(), e.ownedProperty()),
                r.role(), r.factionId(), r.relationships(), r.memory(), r.action());
    }
}
