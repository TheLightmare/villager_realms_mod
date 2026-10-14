package com.lightmare.villagerrealms.core.consequence;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.record.DeathRecord;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsequencesTest {

    private static NPCRecord blank() {
        NPCRecord r = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(), r.vitals(),
                NPCInventory.EMPTY, r.economy(), r.role(), r.factionId(),
                Relationships.EMPTY, MemoryLog.empty(), r.action());
    }

    private static MemoryEvent last(NPCRecord r) {
        return r.memory().events().get(r.memory().events().size() - 1);
    }

    @Test
    void attackedByPlayerRemembersAndHoldsAGrudge() {
        UUID player = UUID.randomUUID();
        NPCRecord out = Consequences.attacked(blank(), Refs.player(player), 3.5f, 100L);

        MemoryEvent ev = last(out);
        assertEquals(MemoryKinds.ATTACKED, ev.kind());
        assertEquals(Refs.player(player), ev.subjectRef());
        assertEquals(100L, ev.tick());
        assertEquals(Consequences.ATTACK_PERSONAL_DELTA, out.relationships().opinions().get(player));
    }

    @Test
    void attackedByAnonymousMobRemembersWithoutAGrudge() {
        NPCRecord out = Consequences.attacked(blank(), Refs.entity("minecraft:zombie"), 2f, 100L);
        assertEquals(MemoryKinds.ATTACKED, last(out).kind());
        assertTrue(out.relationships().opinions().isEmpty(), "no uuid → no personal opinion entry");
    }

    @Test
    void grudgesAccumulateAndClamp() {
        UUID player = UUID.randomUUID();
        NPCRecord r = blank();
        for (int i = 0; i < 40; i++) r = Consequences.attacked(r, Refs.player(player), 1f, i);
        assertEquals(Opinions.MIN, r.relationships().opinions().get(player));
        assertEquals(MemoryLog.DEFAULT_CAPACITY, r.memory().events().size(), "ring buffer caps memory");
    }

    @Test
    void witnessingAKillingBlamesTheKiller() {
        UUID player = UUID.randomUUID();
        DeathRecord d = new DeathRecord(UUID.randomUUID(), "Tom", "farmer", 50L, DeathCause.PLAYER, Refs.player(player));
        NPCRecord out = Consequences.witnessedDeath(blank(), d, 51L);

        MemoryEvent ev = last(out);
        assertEquals(MemoryKinds.DEATH_WITNESSED, ev.kind());
        assertEquals(Refs.player(player), ev.subjectRef());
        assertTrue(ev.detail().contains("Tom"));
        assertEquals(Consequences.WITNESS_KILL_PERSONAL_DELTA, out.relationships().opinions().get(player));
    }

    @Test
    void witnessingStarvationBlamesNobody() {
        DeathRecord d = new DeathRecord(UUID.randomUUID(), "Tom", "farmer", 50L, DeathCause.STARVATION, null);
        NPCRecord out = Consequences.witnessedDeath(blank(), d, 51L);
        assertEquals(MemoryKinds.DEATH_WITNESSED, last(out).kind());
        assertNull(last(out).subjectRef());
        assertTrue(out.relationships().opinions().isEmpty());
    }

    @Test
    void giftLandsInInventoryAsGiftedAndWarmsOpinion() {
        UUID player = UUID.randomUUID();
        NPCRecord out = Consequences.giftReceived(blank(), Refs.player(player), "minecraft:bread", 1, 7L);

        var entry = out.inventory().items().get(0);
        assertEquals("minecraft:bread", entry.itemId());
        assertEquals(1, entry.count());
        assertEquals(Provenance.GIFTED, entry.provenance());
        assertEquals(7L, entry.acquiredAtTick());
        assertEquals(MemoryKinds.GIFT_RECEIVED, last(out).kind());
        assertEquals(Consequences.GIFT_PERSONAL_DELTA, out.relationships().opinions().get(player));
    }

    @Test
    void giftOfNothingIsANoop() {
        NPCRecord r = blank();
        assertSame(r, Consequences.giftReceived(r, Refs.player(UUID.randomUUID()), "", 1, 1L));
        assertSame(r, Consequences.giftReceived(r, Refs.player(UUID.randomUUID()), "minecraft:bread", 0, 1L));
    }

    @Test
    void roleChangeSwapsRoleAndRemembers() {
        NPCRecord out = Consequences.roleChanged(blank(), new RoleState("baker", "1,2,3", 0L), 9L);
        assertEquals("baker", out.role().roleId());
        assertEquals(MemoryKinds.ROLE_CHANGED, last(out).kind());
        assertEquals("farmer -> baker", last(out).detail());
    }

    @Test
    void personalDeltaBackToZeroRemovesTheSparseEntry() {
        UUID p = UUID.randomUUID();
        NPCRecord r = Consequences.adjustPersonal(blank(), p, 5);
        assertEquals(5, r.relationships().opinions().get(p));
        r = Consequences.adjustPersonal(r, p, -5);
        assertFalse(r.relationships().opinions().containsKey(p), "zero means no override; entry dropped");
    }
}
