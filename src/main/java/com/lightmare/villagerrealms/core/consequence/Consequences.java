package com.lightmare.villagerrealms.core.consequence;

import com.lightmare.villagerrealms.core.economy.InventoryOps;
import com.lightmare.villagerrealms.core.record.DeathRecord;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;

import java.util.UUID;

/**
 * Pure per-NPC reactions to gameplay events: each returns a new record with
 * a memory event appended and, where the subject is an identifiable actor
 * (player or NPC ref), a sparse personal-opinion delta. Faction-level
 * standing is a separate concern — see {@link Standing} — because it lives
 * in the global FactionStore, not on the record.
 *
 * Personal deltas are deliberately larger than faction deltas: the victim
 * holds the grudge, the faction merely notes it.
 */
public final class Consequences {

    public static final int ATTACK_PERSONAL_DELTA = -10;
    public static final int WITNESS_KILL_PERSONAL_DELTA = -20;
    public static final int GIFT_PERSONAL_DELTA = 5;

    private Consequences() {}

    /** The NPC was damaged by {@code attackerRef} (see {@link Refs}). */
    public static NPCRecord attacked(NPCRecord victim, String attackerRef, float amount, long tick) {
        NPCRecord out = remember(victim, new MemoryEvent(
                tick, MemoryKinds.ATTACKED, attackerRef, String.format("%.1f", amount)));
        return adjustPersonal(out, Refs.uuidOf(attackerRef), ATTACK_PERSONAL_DELTA);
    }

    /** A village-mate of {@code witness} died. */
    public static NPCRecord witnessedDeath(NPCRecord witness, DeathRecord death, long tick) {
        String detail = death.name() + " died (" + death.cause() + ")";
        NPCRecord out = remember(witness, new MemoryEvent(
                tick, MemoryKinds.DEATH_WITNESSED, death.killerRef(), detail));
        return adjustPersonal(out, Refs.uuidOf(death.killerRef()), WITNESS_KILL_PERSONAL_DELTA);
    }

    /** {@code giverRef} handed the NPC {@code count} of {@code itemId}; it goes into inventory as GIFTED. */
    public static NPCRecord giftReceived(NPCRecord recipient, String giverRef, String itemId, int count, long tick) {
        if (itemId == null || itemId.isEmpty() || count <= 0) return recipient;
        NPCInventory inv = InventoryOps.add(recipient.inventory(), itemId, count, Provenance.GIFTED, tick);
        NPCRecord out = withInventory(recipient, inv);
        out = remember(out, new MemoryEvent(
                tick, MemoryKinds.GIFT_RECEIVED, giverRef, count + "x " + itemId));
        return adjustPersonal(out, Refs.uuidOf(giverRef), GIFT_PERSONAL_DELTA);
    }

    /** Change the NPC's role and remember it. */
    public static NPCRecord roleChanged(NPCRecord npc, RoleState newRole, long tick) {
        if (newRole == null) throw new IllegalArgumentException("newRole required");
        String from = npc.role() == null ? "" : npc.role().roleId();
        NPCRecord out = new NPCRecord(
                npc.dataVersion(), npc.identity(), npc.location(), npc.vitals(),
                npc.inventory(), npc.economy(), newRole, npc.factionId(),
                npc.relationships(), npc.memory(), npc.action());
        return remember(out, new MemoryEvent(
                tick, MemoryKinds.ROLE_CHANGED, null, from + " -> " + newRole.roleId()));
    }

    /** Append to the memory ring buffer. */
    public static NPCRecord remember(NPCRecord npc, MemoryEvent event) {
        MemoryLog log = npc.memory() == null ? MemoryLog.empty() : npc.memory();
        return withMemory(npc, log.append(event));
    }

    /** Apply a clamped delta to the NPC's sparse personal opinion of {@code target}; no-op for null. */
    public static NPCRecord adjustPersonal(NPCRecord npc, UUID target, int delta) {
        if (target == null || delta == 0) return npc;
        Relationships rel = npc.relationships() == null ? Relationships.EMPTY : npc.relationships();
        int current = rel.opinions().getOrDefault(target, 0);
        int next = Opinions.clamp(current + delta);
        if (next == current) return npc;
        return withRelationships(npc, rel.withOpinion(target, next));
    }

    private static NPCRecord withMemory(NPCRecord r, MemoryLog memory) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(), r.vitals(),
                r.inventory(), r.economy(), r.role(), r.factionId(),
                r.relationships(), memory, r.action());
    }

    private static NPCRecord withRelationships(NPCRecord r, Relationships rel) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(), r.vitals(),
                r.inventory(), r.economy(), r.role(), r.factionId(),
                rel, r.memory(), r.action());
    }

    private static NPCRecord withInventory(NPCRecord r, NPCInventory inv) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(), r.vitals(),
                inv, r.economy(), r.role(), r.factionId(),
                r.relationships(), r.memory(), r.action());
    }
}
