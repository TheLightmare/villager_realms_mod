package com.lightmare.villagerrealms.core.consequence;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.relationships.OpinionResolver;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingAndRefsTest {

    @Test
    void refsRoundTrip() {
        UUID u = UUID.randomUUID();
        assertEquals(u, Refs.uuidOf(Refs.player(u)));
        assertEquals(u, Refs.uuidOf(Refs.npc(u)));
        assertTrue(Refs.isPlayer(Refs.player(u)));
        assertFalse(Refs.isPlayer(Refs.npc(u)));
        assertNull(Refs.uuidOf(Refs.entity("minecraft:zombie")));
        assertNull(Refs.uuidOf("player:not-a-uuid"));
        assertNull(Refs.uuidOf(null));
    }

    @Test
    void standingAccumulatesPerFactionPerTargetAndClamps() {
        UUID player = UUID.randomUUID();
        String ref = Refs.player(player);
        FactionRelations rel = FactionRelations.EMPTY;

        rel = Standing.adjust(rel, "faction:a", ref, Standing.ATTACK_DELTA);
        rel = Standing.adjust(rel, "faction:a", ref, Standing.ATTACK_DELTA);
        assertEquals(2 * Standing.ATTACK_DELTA, Standing.of(rel, "faction:a", ref));
        assertEquals(0, Standing.of(rel, "faction:b", ref), "other factions unaffected");

        for (int i = 0; i < 10; i++) rel = Standing.adjust(rel, "faction:a", ref, Standing.KILL_DELTA);
        assertEquals(Opinions.MIN, Standing.of(rel, "faction:a", ref));

        rel = Standing.adjust(rel, "faction:a", ref, Standing.GIFT_DELTA);
        assertEquals(Opinions.MIN + Standing.GIFT_DELTA, Standing.of(rel, "faction:a", ref));
    }

    @Test
    void standingIgnoresDegenerateInput() {
        FactionRelations rel = FactionRelations.EMPTY;
        assertSame(rel, Standing.adjust(rel, "", "player:x", -5));
        assertSame(rel, Standing.adjust(rel, "faction:a", null, -5));
        assertSame(rel, Standing.adjust(rel, "faction:a", "player:x", 0));
        assertEquals(0, Standing.of(null, "faction:a", "player:x"));
    }

    @Test
    void resolverSeesPlayerStandingThroughTheFactionMatrix() {
        UUID player = UUID.randomUUID();
        String ref = Refs.player(player);
        NPCRecord viewer = Fixtures.npc(UUID.randomUUID(), "v", 0, 0); // faction:v
        FactionRelations rel = Standing.adjust(FactionRelations.EMPTY, "faction:v", ref, -30);

        assertEquals(-30, OpinionResolver.effectiveTowards(viewer, ref, player, rel));

        // A personal grudge overrides the faction's stance.
        NPCRecord grudge = Consequences.adjustPersonal(viewer, player, -80);
        assertEquals(-80, OpinionResolver.effectiveTowards(grudge, ref, player, rel));

        // No uuid (anonymous mob) → faction matrix only.
        assertEquals(0, OpinionResolver.effectiveTowards(viewer, Refs.entity("minecraft:zombie"), null, rel));
    }

    @Test
    void resolverNpcOverloadStillWorks() {
        NPCRecord viewer = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        NPCRecord other = Fixtures.npc(UUID.randomUUID(), "w", 0, 0);
        FactionRelations rel = FactionRelations.EMPTY.withOpinion("faction:v", "faction:w", 12);
        assertEquals(12, OpinionResolver.effective(viewer, other, rel));
    }
}
