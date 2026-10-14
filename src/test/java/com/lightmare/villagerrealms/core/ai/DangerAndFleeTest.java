package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.actions.EatAction;
import com.lightmare.villagerrealms.core.ai.actions.FleeAction;
import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;
import com.lightmare.villagerrealms.core.ai.considerations.DangerConsideration;
import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DangerAndFleeTest {

    private static NPCRecord calm(Vitals v) {
        NPCRecord r = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(), v, r.inventory(),
                r.economy(), r.role(), r.factionId(), r.relationships(), MemoryLog.empty(), r.action());
    }

    private static NPCRecord hitAt(NPCRecord r, long tick) {
        return Consequences.attacked(r, Refs.player(UUID.randomUUID()), 2f, tick);
    }

    @Test
    void nothingScaryScoresZero() {
        NPCRecord r = calm(Vitals.FRESH);
        assertEquals(0f, new DangerConsideration().score(EvalContext.of(r, 1000L)));
        assertEquals(0f, new DangerConsideration().score(new EvalContext(r, 1000L, 1000L, null, ThreatView.NONE)));
    }

    @Test
    void recentAttackFadesOverTheWindow() {
        NPCRecord r = hitAt(calm(Vitals.FRESH), 1000L);
        DangerConsideration d = new DangerConsideration();
        assertEquals(1f, d.score(EvalContext.of(r, 1000L)), 0.001f);
        assertEquals(0.5f, d.score(EvalContext.of(r, 1000L + DangerConsideration.RECENT_ATTACK_TICKS / 2)), 0.001f);
        assertEquals(0f, d.score(EvalContext.of(r, 1000L + DangerConsideration.RECENT_ATTACK_TICKS)), 0.001f);
    }

    @Test
    void nearbyThreatScoresByProximity() {
        NPCRecord r = calm(Vitals.FRESH);
        DangerConsideration d = new DangerConsideration();
        ThreatView onTop = ThreatView.of(0, 64, 0, 0.0, "player:x");
        ThreatView edge = ThreatView.of(16, 64, 0, 16.0 * 16.0, "player:x");
        assertEquals(1f, d.score(new EvalContext(r, 1L, 1L, null, onTop)), 0.001f);
        assertEquals(DangerConsideration.MIN_PROXIMITY_SCORE, d.score(new EvalContext(r, 1L, 1L, null, edge)), 0.001f);
    }

    @Test
    void fleePlansAwayFromAVisibleThreatOrHomeOtherwise() {
        NPCRecord r = calm(Vitals.FRESH);
        List<ActionStep> away = new FleeAction().plan(new EvalContext(r, 1L, 1L, null, ThreatView.of(3.5, 64, -2, 9, "player:x")));
        assertEquals(1, away.size());
        assertEquals("flee", away.get(0).kind());
        assertEquals("3.5,64.0,-2.0", away.get(0).target());

        List<ActionStep> home = new FleeAction().plan(EvalContext.of(r, 1L));
        assertEquals("pathfind", home.get(0).kind());
        assertEquals("bed", home.get(0).target());
    }

    @Test
    void justHitAndHungryNpcRunsFirstThenEatsOnceCalm() {
        // Night (18000) so work and sleep stay out of it: hunger 5 → eat ≈ 0.56,
        // fresh hit → danger 1.0. Once the memory fades, eating wins.
        NPCRecord hungry = calm(new Vitals(20f, 5f, 1f, 0.5f));
        NPCRecord hit = hitAt(hungry, 18000L);
        Decision now = Evaluators.full().evaluate(EvalContext.of(hit, 18000L), new ConsiderationCache());
        assertEquals(FleeAction.ID, now.action().id());

        long calmAgain = 18000L + DangerConsideration.RECENT_ATTACK_TICKS + 1;
        Decision later = Evaluators.full().evaluate(new EvalContext(hit, calmAgain, 18000L, null, null), new ConsiderationCache());
        assertEquals(EatAction.ID, later.action().id());
    }

    @Test
    void strippedEvaluatorKeepsFlee() {
        boolean has = Evaluators.stripped().candidates().stream().anyMatch(a -> a.id().equals(FleeAction.ID));
        assertTrue(has, "Tier 1 must still run from danger on memory alone");
    }

    @Test
    void hostilePlayerNearbyMakesAnOtherwiseContentNpcFlee() {
        NPCRecord r = calm(Vitals.FRESH);
        ThreatView near = ThreatView.of(2, 64, 2, 8.0, "player:x");
        Decision d = Evaluators.full().evaluate(new EvalContext(r, 18000L, 18000L, null, near), new ConsiderationCache());
        assertEquals(FleeAction.ID, d.action().id());
    }
}
