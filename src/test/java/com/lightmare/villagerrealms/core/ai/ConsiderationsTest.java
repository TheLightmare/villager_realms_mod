package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.considerations.HungerConsideration;
import com.lightmare.villagerrealms.core.ai.considerations.NightTimeConsideration;
import com.lightmare.villagerrealms.core.ai.considerations.SleepConsideration;
import com.lightmare.villagerrealms.core.ai.considerations.WorkTimeConsideration;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsiderationsTest {

    private static NPCRecord withVitals(Vitals v) {
        NPCRecord base = Fixtures.npc(UUID.randomUUID(), "village", 0, 0);
        return new NPCRecord(
                base.dataVersion(), base.identity(), base.location(),
                v, base.inventory(), base.economy(), base.role(),
                base.factionId(), base.relationships(), base.memory(), base.action());
    }

    private static NPCRecord withRole(RoleState r) {
        NPCRecord base = Fixtures.npc(UUID.randomUUID(), "village", 0, 0);
        return new NPCRecord(
                base.dataVersion(), base.identity(), base.location(),
                base.vitals(), base.inventory(), base.economy(), r,
                base.factionId(), base.relationships(), base.memory(), base.action());
    }

    @Test
    void hungerScoreRisesAsHungerDrops() {
        var c = new HungerConsideration();
        float full = c.score(EvalContext.of(withVitals(new Vitals(20f, 20f, 1f, 0.5f)), 0));
        float half = c.score(EvalContext.of(withVitals(new Vitals(20f, 10f, 1f, 0.5f)), 0));
        float empty = c.score(EvalContext.of(withVitals(new Vitals(20f, 0f, 1f, 0.5f)), 0));

        assertEquals(0f, full, 1e-5f);
        assertEquals(1f, empty, 1e-5f);
        assertTrue(half > 0f && half < 1f);
    }

    @Test
    void sleepScoreRisesAsEnergyDrops() {
        var c = new SleepConsideration();
        float rested = c.score(EvalContext.of(withVitals(new Vitals(20f, 20f, 1f, 0.5f)), 0));
        float exhausted = c.score(EvalContext.of(withVitals(new Vitals(20f, 20f, 0f, 0.5f)), 0));

        assertEquals(0f, rested, 1e-5f);
        assertEquals(1f, exhausted, 1e-5f);
    }

    @Test
    void workTimeIsZeroWithoutWorkplace() {
        var c = new WorkTimeConsideration();
        var noWorkplace = withRole(new RoleState("laborer", "", 0L));
        assertEquals(0f, c.score(EvalContext.of(noWorkplace, 6000L)));
    }

    @Test
    void workTimeIsZeroAtNight() {
        var c = new WorkTimeConsideration();
        var npc = withRole(new RoleState("farmer", "workstation:farmer@0,64,0", 0L));
        assertEquals(0f, c.score(EvalContext.of(npc, 18000L)));
    }

    @Test
    void workTimePeaksAtMidday() {
        var c = new WorkTimeConsideration();
        var npc = withRole(new RoleState("farmer", "workstation:farmer@0,64,0", 0L));
        float midday = c.score(EvalContext.of(npc, 6000L));
        float dawn = c.score(EvalContext.of(npc, 200L));
        assertEquals(1f, midday, 1e-5f);
        assertTrue(dawn < midday);
        assertTrue(dawn > 0f);
    }

    @Test
    void nightTimeIsZeroDuringDay() {
        var c = new NightTimeConsideration();
        var npc = Fixtures.npc(java.util.UUID.randomUUID(), "village", 0, 0);
        assertEquals(0f, c.score(EvalContext.of(npc, 0L)));
        assertEquals(0f, c.score(EvalContext.of(npc, 6000L)));
        assertEquals(0f, c.score(EvalContext.of(npc, 11999L)));
    }

    @Test
    void nightTimeIsOneAtNight() {
        var c = new NightTimeConsideration();
        var npc = Fixtures.npc(java.util.UUID.randomUUID(), "village", 0, 0);
        assertEquals(1f, c.score(EvalContext.of(npc, 12000L)));
        assertEquals(1f, c.score(EvalContext.of(npc, 18000L)));
        assertEquals(1f, c.score(EvalContext.of(npc, 23999L)));
    }

    @Test
    void nightTimeWrapsAcrossDayBoundary() {
        var c = new NightTimeConsideration();
        var npc = Fixtures.npc(java.util.UUID.randomUUID(), "village", 0, 0);
        // Day 5, midnight (5 * 24000 + 18000)
        assertEquals(1f, c.score(EvalContext.of(npc, 138000L)));
        // Day 5, midday
        assertEquals(0f, c.score(EvalContext.of(npc, 126000L)));
    }
}
