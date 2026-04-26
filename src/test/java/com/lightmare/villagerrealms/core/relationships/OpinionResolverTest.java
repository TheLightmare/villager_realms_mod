package com.lightmare.villagerrealms.core.relationships;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Relationships;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpinionResolverTest {

    private static NPCRecord withFaction(NPCRecord r, String faction) {
        return r.withFactionId(faction);
    }

    private static NPCRecord withRelationships(NPCRecord r, Relationships rel) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(),
                r.vitals(), r.inventory(), r.economy(), r.role(),
                r.factionId(), rel, r.memory(), r.action());
    }

    @Test
    void neutralWhenNoFactionsAndNoOverrides() {
        NPCRecord viewer = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), NPCRecord.NO_FACTION),
                Relationships.EMPTY);
        NPCRecord target = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), NPCRecord.NO_FACTION),
                Relationships.EMPTY);
        assertEquals(0, OpinionResolver.effective(viewer, target, FactionRelations.EMPTY));
    }

    @Test
    void factionMatrixIsBaselineWhenNoOverridesPresent() {
        NPCRecord viewer = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), "faction:a"),
                Relationships.EMPTY);
        NPCRecord target = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), "faction:b"),
                Relationships.EMPTY);
        FactionRelations rel = FactionRelations.EMPTY.withOpinion("faction:a", "faction:b", -30);
        assertEquals(-30, OpinionResolver.effective(viewer, target, rel));
    }

    @Test
    void factionOverrideTakesPrecedenceOverMatrix() {
        NPCRecord viewer = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), "faction:a"),
                new Relationships(Map.of("faction:b", 50), Map.of()));
        NPCRecord target = withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), "faction:b");
        FactionRelations rel = FactionRelations.EMPTY.withOpinion("faction:a", "faction:b", -30);
        // viewer personally likes faction:b despite faction matrix saying otherwise.
        assertEquals(50, OpinionResolver.effective(viewer, target, rel));
    }

    @Test
    void perNpcOverrideTakesPrecedenceOverFactionOverride() {
        UUID targetId = UUID.randomUUID();
        NPCRecord target = withFaction(
                Fixtures.npc(targetId, "v", 0, 0), "faction:b");
        NPCRecord viewer = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), "faction:a"),
                new Relationships(
                        Map.of("faction:b", 50),
                        Map.of(targetId, -100)));
        FactionRelations rel = FactionRelations.EMPTY.withOpinion("faction:a", "faction:b", -30);
        // Per-NPC sparse override wins over everything else.
        assertEquals(-100, OpinionResolver.effective(viewer, target, rel));
    }

    @Test
    void zeroPerNpcOverrideStillWinsOverFactionMatrix() {
        // The override is "I have no specific feelings about this individual",
        // which deliberately suppresses the faction-level baseline.
        UUID targetId = UUID.randomUUID();
        NPCRecord target = withFaction(
                Fixtures.npc(targetId, "v", 0, 0), "faction:b");
        NPCRecord viewer = withRelationships(withFaction(
                Fixtures.npc(UUID.randomUUID(), "v", 0, 0), "faction:a"),
                new Relationships(Map.of(), Map.of(targetId, 0)));
        FactionRelations rel = FactionRelations.EMPTY.withOpinion("faction:a", "faction:b", -30);
        assertEquals(0, OpinionResolver.effective(viewer, target, rel));
    }
}
